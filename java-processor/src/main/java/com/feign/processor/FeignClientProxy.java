package com.feign.processor;

import com.feign.framework.FeignException;
import com.feign.framework.Response;
import com.feign.framework.annotations.FeignMethod;
import com.feign.framework.annotations.Path;
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
    private final Class<?> fallbackClass;
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
        this(metadata, List.of(), null, null, null, null, null, Void.class);
    }

    public FeignClientProxy(FeignClientMetadata metadata, List<FeignInterceptor> interceptors,
                             LoadBalancer loadBalancer, ProtocolHandler protocolHandler,
                             Decoder decoder, Encoder encoder,
                             ServiceDiscovery serviceDiscovery, Class<?> fallbackClass) {
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
        this.fallbackClass = fallbackClass != null && fallbackClass != Void.class ? fallbackClass : null;
    }

    // -- InvocationHandler --

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) return method.invoke(this, args);

        FeignMethod methodAnn = method.getAnnotation(FeignMethod.class);
        if (methodAnn == null) throw new FeignException("Method " + method.getName() + " not @FeignMethod");

        try {
            // 1. Build request (encode body via Encoder)
            Request request = buildRequest(methodAnn, method, args);
            // 2. interceptors before
            for (FeignInterceptor i : interceptors) request = i.beforeExecute(request);
            // 3. service discovery
            String targetUrl = resolveViaDiscovery(request);
            // 4. load balancer
            targetUrl = resolveTargetUrl(request, targetUrl);
            // 5. execute
            Type returnType = method.getGenericReturnType();

            if (isAsync(method)) {
                return executeAsyncWithRetry(request, targetUrl, unwrapAsync(method));
            } else {
                Response resp = executeSyncWithRetry(request, targetUrl);
                for (int i = interceptors.size() - 1; i >= 0; i--)
                    resp = interceptors.get(i).afterExecute(resp);
                return decode(resp, returnType);
            }
        } catch (Exception e) {
            if (fallbackClass != null) {
                return invokeFallback(method, args);
            }
            throw e;
        }
    }

    // -- fallback --

    private Object invokeFallback(Method method, Object[] args) throws Exception {
        Object fallbackInstance = fallbackClass.getDeclaredConstructor().newInstance();
        return method.invoke(fallbackInstance, args);
    }

    // -- request building (with Encoder) --

    private Request buildRequest(FeignMethod methodAnn, Method method, Object[] args) throws Exception {
        String path = resolvePath(methodAnn, method, args);
        String fullUrl = metadata.getUrl() + "/" + path;
        Map<String, String> headers = parseHeaders(methodAnn);

        // Encode body using Encoder
        byte[] body = null;
        Object bodyObj = extractBody(method, args);
        if (bodyObj != null) {
            Type bodyType = getBodyType(method);
            body = encoder.encode(bodyObj, bodyType);
        }

        return Request.of(methodAnn.method(), fullUrl, headers,
            body != null ? new String(body) : null, new HashMap<>());
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

    // -- service discovery --

    private String resolveViaDiscovery(Request request) {
        if (serviceDiscovery != null) {
            List<String> instances = serviceDiscovery.getInstances(metadata.getServiceName());
            if (instances != null && !instances.isEmpty()) {
                return instances.get(0); // first instance, LB picks later
            }
        }
        return request.getUrl();
    }

    // -- sync / async execution --

    private Response executeSyncWithRetry(Request req, String url) throws FeignException {
        FeignException last = null;
        int max = retryPolicy != null ? retryPolicy.getMaxRetries() : 0;
        for (int a = 0; a <= max; a++) {
            try {
                Response resp = protocolHandler.execute(rebuildUrl(req, url));
                markLbComplete(url);
                return resp;
            } catch (FeignException e) {
                markLbComplete(url); last = e; notifyError(req, e);
                if (!shouldRetry(e, a)) throw e; sleep(retryPolicy.getRetryInterval());
            } catch (Exception e) {
                markLbComplete(url); last = new FeignException("Request failed: " + url, e);
                notifyError(req, last);
                if (!shouldRetry(e, a)) throw last; sleep(retryPolicy.getRetryInterval());
            }
        }
        throw last != null ? last : new FeignException("Max retries exceeded: " + url);
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

    // -- load balancing --

    private String resolveTargetUrl(Request req, String baseUrl) {
        if (loadBalancer != null) {
            List<String> servers = getServers();
            if (servers != null && !servers.isEmpty()) return loadBalancer.select(req, servers);
        }
        return baseUrl;
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
            orig.getBody() != null ? new String(orig.getBody()) : null, orig.getQueryParams());
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
