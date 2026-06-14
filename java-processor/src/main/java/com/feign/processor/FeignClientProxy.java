package com.feign.processor;

import com.feign.framework.FeignException;
import com.feign.framework.Response;
import com.feign.framework.annotations.FeignMethod;
import com.feign.framework.annotations.Path;
import com.feign.framework.annotations.Query;
import com.feign.framework.circuit.CircuitBreaker;
import com.feign.framework.codec.Decoder;
import com.feign.framework.codec.Encoder;
import com.feign.framework.codec.GsonDecoder;
import com.feign.framework.codec.GsonEncoder;
import com.feign.framework.discovery.ServiceDiscovery;
import com.feign.framework.http.Request;
import com.feign.framework.interceptor.FeignInterceptor;
import com.feign.framework.loadbalancer.*;
import com.feign.framework.protocol.*;
import com.feign.framework.retry.*;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Dynamic proxy for @FeignClient interfaces.
 *
 * <h3>Execution pipeline</h3>
 * <pre>
 * call → encode body → build request → interceptors(before) → service discovery
 *      → load balancer → retry loop → protocolHandler → decoder → interceptors(after)
 *      → return typed object
 *      → (on total failure) → fallback
 * </pre>
 */
public class FeignClientProxy implements InvocationHandler {

    private final FeignClientMetadata metadata;
    private final ProtocolHandler protocolHandler;
    private final List<FeignInterceptor> interceptors;
    private final LoadBalancer loadBalancer;
    private final RetryPolicy retryPolicy;
    private final Decoder decoder;
    private final Encoder encoder;
    private final ServiceDiscovery serviceDiscovery;
    private final CircuitBreaker circuitBreaker;
    private final Class<?> fallbackClass;
    private Object fallbackInstance; // lazy singleton — holds the fallback implementation
    private final int connectTimeout, readTimeout;

    // -- protocol registry --
    static final List<ProtocolHandler> builtinHandlers = new ArrayList<>();
    static {
        builtinHandlers.add(new HttpProtocolHandler(5000, 5000));
        builtinHandlers.add(new GrpcProtocolHandler());
        builtinHandlers.add(new WebSocketProtocolHandler());
    }
    public static void addProtocolHandler(ProtocolHandler h) { builtinHandlers.add(h); }

    // -- constructors --

    public FeignClientProxy(FeignClientMetadata metadata) {
        this(metadata, null, null, null, null, null, null, null, Void.class);
    }

    public FeignClientProxy(FeignClientMetadata metadata, List<FeignInterceptor> interceptors,
                             LoadBalancer loadBalancer, ProtocolHandler protocolHandler,
                             Decoder decoder, Encoder encoder,
                             ServiceDiscovery serviceDiscovery, CircuitBreaker circuitBreaker,
                             Class<?> fallbackClass) {
        this.metadata = metadata;
        this.interceptors = new ArrayList<>(interceptors);
        this.interceptors.sort(Comparator.comparingInt(FeignInterceptor::order));
        this.connectTimeout = metadata.getConnectTimeout() > 0 ? metadata.getConnectTimeout() : 5000;
        this.readTimeout = metadata.getReadTimeout() > 0 ? metadata.getReadTimeout() : 5000;
        this.protocolHandler = protocolHandler != null ? protocolHandler : selectProtocol(metadata.getUrl());
        this.loadBalancer = loadBalancer != null ? loadBalancer : defaultLb();
        this.retryPolicy = defaultRetry();
        this.decoder = decoder != null ? decoder : new GsonDecoder();
        this.encoder = encoder != null ? encoder : new GsonEncoder();
        this.serviceDiscovery = serviceDiscovery;
        this.circuitBreaker = circuitBreaker;
        this.fallbackClass = fallbackClass != null && fallbackClass != Void.class ? fallbackClass : null;
    }

    // -- InvocationHandler --

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) return method.invoke(this, args);

        FeignMethod methodAnn = method.getAnnotation(FeignMethod.class);
        if (methodAnn == null) throw new FeignException("Method " + method.getName() + " not @FeignMethod");

        try {
            // 0. Circuit breaker — fast-fail if open
            if (circuitBreaker != null && !circuitBreaker.allowRequest()) {
                if (fallbackClass != null) return invokeFallback(method, args);
                throw new FeignException("Circuit breaker OPEN for " + metadata.getServiceName());
            }

            // 1. Build path + query params + headers + body (NO base URL yet)
            String resolvedPath = resolvePath(methodAnn, method, args);
            Map<String, String> queryParams = extractQueryParams(method, args);
            Map<String, String> headers = parseHeaders(methodAnn);
            byte[] body = encodeBody(method, args, methodAnn);

            // 2. Resolve base URL: annotation → discovery → load balancer
            String baseUrl = resolveBaseUrl();

            // 3. Assemble full URL
            if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            if (resolvedPath.startsWith("/")) resolvedPath = resolvedPath.substring(1);
            String fullUrl = baseUrl + "/" + resolvedPath;
            if (!queryParams.isEmpty()) {
                fullUrl += "?" + queryParams.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .reduce((a, b) -> a + "&" + b).orElse("");
            }

            // 4. Build request
            Request request = Request.of(methodAnn.method(), fullUrl, headers, body, queryParams);

            // 5. Interceptors before
            for (FeignInterceptor i : interceptors) request = i.beforeExecute(request);

            // 6. Execute
            Type returnType = method.getGenericReturnType();
            if (isAsync(method)) {
                return executeAsyncWithRetry(request, fullUrl, unwrapAsync(method));
            } else {
                Response resp = executeSyncWithRetry(request, fullUrl);
                for (int i = interceptors.size() - 1; i >= 0; i--)
                    resp = interceptors.get(i).afterExecute(resp);
                return decode(resp, returnType);
            }
        } catch (Exception e) {
            if (fallbackClass != null) return invokeFallback(method, args);
            throw e;
        }
    }

    // ── URL resolution pipeline ──

    /** annotation URL → discovery → load balancer */
    private String resolveBaseUrl() {
        // 1. Start from annotation URL (may be empty if using discovery)
        String url = metadata.getUrl();

        // 2. Service discovery overrides
        if (serviceDiscovery != null) {
            List<String> instances = serviceDiscovery.getInstances(metadata.getServiceName());
            if (instances != null && !instances.isEmpty()) {
                url = instances.get(0); // seed, LB picks later
            }
        }

        // 3. Load balancer selects one from available servers
        if (loadBalancer != null) {
            List<String> servers = getServers();
            if (servers != null && !servers.isEmpty()) {
                url = loadBalancer.select(null, servers);
            }
        }

        if (url == null || url.isEmpty()) {
            throw new FeignException("No URL configured for service: " + metadata.getServiceName()
                + ". Set url in @FeignClient or configure ServiceDiscovery.");
        }
        return url;
    }

    // ── body encoding ──

    private byte[] encodeBody(Method method, Object[] args, FeignMethod ann) throws Exception {
        String contentType = ann.contentType();
        // form-encoded: collect @Query and non-path params into key=value pairs
        if ("application/x-www-form-urlencoded".equals(contentType)) {
            Map<String, String> form = new LinkedHashMap<>();
            Parameter[] params = method.getParameters();
            for (int i = 0; i < params.length; i++) {
                if (params[i].getAnnotation(Path.class) == null
                    && args != null && i < args.length && args[i] != null
                    && isPrimitiveOrWrapper(params[i].getType())) {
                    String key = params[i].getAnnotation(Query.class) != null
                        ? params[i].getAnnotation(Query.class).value()
                        : params[i].getName();
                    form.put(key, args[i].toString());
                }
            }
            if (!form.isEmpty()) {
                return form.entrySet().stream()
                    .map(e -> {
                        try {
                            return e.getKey() + "=" + java.net.URLEncoder.encode(e.getValue(), "UTF-8");
                        } catch (UnsupportedEncodingException ex) {
                            throw new RuntimeException(ex);
                        }
                    })
                    .reduce((a, b) -> a + "&" + b).orElse("").getBytes();
            }
            return null;
        }
        // JSON body (default)
        Object bodyObj = extractBody(method, args);
        if (bodyObj == null) return null;
        Type bodyType = getBodyType(method);
        return encoder.encode(bodyObj, bodyType);
    }

    /** Inject a pre-built fallback instance (e.g., from Spring context). */
    public void setFallbackInstance(Object instance) {
        this.fallbackInstance = instance;
    }

    /**
     * Invoke fallback. The fallback class implements the same interface,
     * so the proxy's {@code method} (from the interface) works on the fallback instance.
     */
    private Object invokeFallback(Method method, Object[] args) throws Exception {
        if (fallbackInstance == null) {
            synchronized (this) {
                if (fallbackInstance == null) {
                    fallbackInstance = fallbackClass.getDeclaredConstructor().newInstance();
                }
            }
        }
        return method.invoke(fallbackInstance, args);
    }

    private Type getBodyType(Method method) {
        Parameter[] params = method.getParameters();
        for (Parameter p : params) {
            if (p.getAnnotation(Path.class) == null && !isPrimitiveOrWrapper(p.getType())) {
                return p.getParameterizedType();
            }
        }
        return method.getGenericReturnType();
    }

    // -- sync / async execution --

    private Response executeSyncWithRetry(Request req, String originalUrl) throws FeignException {
        FeignException last = null;
        int max = retryPolicy != null ? retryPolicy.getMaxRetries() : 0;
        for (int a = 0; a <= max; a++) {
            // Re-resolve base URL on retry — LB may pick a different healthy server
            String url = a == 0 ? originalUrl : rebuildFullUrl(req, resolveBaseUrl());
            try {
                Response resp = protocolHandler.execute(rebuildUrl(req, url));
                markLbComplete(url);
                if (circuitBreaker != null) circuitBreaker.onSuccess();
                return resp;
            } catch (FeignException e) {
                markLbComplete(url); last = e; notifyError(req, e);
                if (circuitBreaker != null) circuitBreaker.onFailure();
                if (!shouldRetry(e, a)) throw e; sleep(retryPolicy.getRetryInterval());
            } catch (Exception e) {
                markLbComplete(url); last = new FeignException("Request failed: " + url, e);
                notifyError(req, last);
                if (circuitBreaker != null) circuitBreaker.onFailure();
                if (!shouldRetry(e, a)) throw last; sleep(retryPolicy.getRetryInterval());
            }
        }
        throw last != null ? last : new FeignException("Max retries exceeded");
    }

    /** Rebuild URL with a new base (from LB re-selection) but same path. */
    private String rebuildFullUrl(Request req, String newBase) {
        // Extract path from original URL: "http://host/api/users/1" → "/api/users/1"
        String originalUrl = req.getUrl();
        int pathStart = originalUrl.indexOf("/", originalUrl.indexOf("://") + 3);
        String path = pathStart > 0 ? originalUrl.substring(pathStart) : "";
        if (newBase.endsWith("/")) newBase = newBase.substring(0, newBase.length() - 1);
        if (!path.startsWith("/")) path = "/" + path;
        return newBase + path;
    }

    private CompletableFuture<Object> executeAsyncWithRetry(Request req, String url, Type innerType) {
        return executeAsyncWithRetry(req, url, 0, innerType, new CompletableFuture<>());
    }

    private CompletableFuture<Object> executeAsyncWithRetry(Request req, String url, int attempt,
                                                             Type innerType, CompletableFuture<Object> f) {
        protocolHandler.executeAsync(rebuildUrl(req, url)).thenAccept(resp -> {
            Response p = resp;
            for (int i = interceptors.size() - 1; i >= 0; i--) p = interceptors.get(i).afterExecute(p);
            try { f.complete(decode(p, innerType)); } catch (Exception e) { f.completeExceptionally(e); }
        }).exceptionally(t -> {
            Throwable c = t.getCause() != null ? t.getCause() : t;
            FeignException fe = c instanceof FeignException ? (FeignException) c
                : new FeignException("Async failed: " + url, c);
            notifyError(req, fe);
            if (shouldRetry(c, attempt)) {
                sleep(retryPolicy.getRetryInterval());
                executeAsyncWithRetry(req, url, attempt + 1, innerType, f);
            } else f.completeExceptionally(fe);
            return null;
        });
        return f;
    }

    // -- decode / encode helpers --

    private Object decode(Response resp, Type type) throws Exception {
        if (type == void.class || type == Void.class) return null;
        if (type == Response.class) return resp;
        if (!resp.successful()) throw new FeignException(resp.statusCode(), resp.getUrl(), resp.getBodyAsString());

        // FeignResponse<T> → wrap decoded body + response headers
        if (type instanceof ParameterizedType pt && pt.getRawType() == com.feign.framework.FeignResponse.class) {
            Type innerType = pt.getActualTypeArguments()[0];
            Object body = decoder.decode(resp, innerType);
            return new com.feign.framework.FeignResponse<>(body, resp.headers());
        }

        return decoder.decode(resp, type);
    }

    // -- path / headers / body extraction --

    private String resolvePath(FeignMethod mAnn, Method method, Object[] args) {
        String[] segs = mAnn.path();
        if (segs.length == 0) return method.getName();
        String path = String.join("/", segs);
        Map<String, String> vars = new HashMap<>();
        Parameter[] params = method.getParameters();
        for (int i = 0; i < params.length; i++) {
            Path p = params[i].getAnnotation(Path.class);
            if (p != null && args != null && i < args.length && args[i] != null)
                vars.put(p.value(), args[i].toString());
        }
        for (var e : vars.entrySet()) path = path.replace("{" + e.getKey() + "}", e.getValue());
        return path;
    }

    private Map<String, String> parseHeaders(FeignMethod mAnn) {
        Map<String, String> h = new HashMap<>();
        for (String s : mAnn.headers()) {
            int c = s.indexOf(':'); if (c > 0) h.put(s.substring(0, c).trim(), s.substring(c + 1).trim());
        }
        return h;
    }

    /** Collect @Query-annotated parameters as key=value pairs. */
    private Map<String, String> extractQueryParams(Method method, Object[] args) {
        Map<String, String> map = new LinkedHashMap<>();
        if (args == null) return map;
        Parameter[] params = method.getParameters();
        for (int i = 0; i < params.length; i++) {
            Query q = params[i].getAnnotation(Query.class);
            if (q != null && args[i] != null) {
                map.put(q.value(), args[i].toString());
            }
        }
        return map;
    }

    private Object extractBody(Method method, Object[] args) {
        if (args == null) return null;
        Parameter[] params = method.getParameters();
        for (int i = 0; i < params.length; i++)
            if (params[i].getAnnotation(Path.class) == null && args[i] != null && !isPrimitiveOrWrapper(params[i].getType()))
                return args[i];
        return null;
    }

    private boolean isPrimitiveOrWrapper(Class<?> t) {
        return t.isPrimitive() || t == String.class || Number.class.isAssignableFrom(t) || Boolean.class.isAssignableFrom(t);
    }

    private List<String> getServers() {
        if (serviceDiscovery != null) {
            List<String> instances = serviceDiscovery.getInstances(metadata.getServiceName());
            if (instances != null && !instances.isEmpty()) return instances;
        }
        if (metadata.getUrl() != null && !metadata.getUrl().isEmpty())
            return List.of(metadata.getUrl());
        return null;
    }

    // -- retry --

    private boolean shouldRetry(Throwable e, int a) {
        return retryPolicy != null && e instanceof Exception && retryPolicy.canRetry((Exception) e, a);
    }
    private void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
    private void notifyError(Request req, FeignException e) { interceptors.forEach(i -> i.onError(req, e)); }
    private void markLbComplete(String url) {
        if (loadBalancer instanceof LeastConnectionsLoadBalancer lb) lb.markComplete(url);
    }
    private Request rebuildUrl(Request orig, String newUrl) {
        return Request.of(orig.getMethod(), newUrl, orig.getHeaders(),
            orig.getBody(), orig.getQueryParams());
    }
    private boolean isAsync(Method m) { return CompletableFuture.class.isAssignableFrom(m.getReturnType()); }
    private Type unwrapAsync(Method m) {
        Type gt = m.getGenericReturnType();
        if (gt instanceof ParameterizedType pt) { Type[] a = pt.getActualTypeArguments(); if (a.length > 0) return a[0]; }
        return Response.class;
    }

    @SuppressWarnings("unchecked")
    public <T> T createProxy(Class<T> c) {
        return (T) Proxy.newProxyInstance(c.getClassLoader(), new Class<?>[]{c}, this);
    }

    // -- defaults --

    private ProtocolHandler selectProtocol(String url) {
        if (url == null || !url.contains("://")) return builtinHandlers.get(0);
        for (ProtocolHandler h : builtinHandlers) {
            String s = h.scheme();
            if (url.startsWith(s + "://") || url.startsWith(s + "s://")) return h;
        }
        return builtinHandlers.get(0);
    }

    private LoadBalancer defaultLb() {
        LoadBalancerType t = metadata.getLoadBalancerType();
        if (t == null) return null;
        return switch (t) {
            case ROUND_ROBIN -> new RoundRobinLoadBalancer();
            case RANDOM -> new RandomLoadBalancer();
            case LEAST_CONNECTIONS -> new LeastConnectionsLoadBalancer();
        };
    }

    private RetryPolicy defaultRetry() {
        DefaultRetryPolicy p = new DefaultRetryPolicy();
        p.setMaxRetries(Math.max(0, metadata.getMaxRetries()));
        p.setRetryInterval(Math.max(0, metadata.getRetryInterval()));
        return p;
    }
}
