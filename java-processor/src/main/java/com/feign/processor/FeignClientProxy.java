package com.feign.processor;

import com.feign.framework.FeignException;
import com.feign.framework.Response;
import com.feign.framework.annotations.FeignMethod;
import com.feign.framework.annotations.Path;
import com.feign.framework.codec.Decoder;
import com.feign.framework.codec.GsonDecoder;
import com.feign.framework.http.Request;
import com.feign.framework.interceptor.FeignInterceptor;
import com.feign.framework.loadbalancer.LoadBalancer;
import com.feign.framework.loadbalancer.LoadBalancerType;
import com.feign.framework.protocol.GrpcProtocolHandler;
import com.feign.framework.protocol.ProtocolHandler;
import com.feign.framework.protocol.HttpProtocolHandler;
import com.feign.framework.protocol.WebSocketProtocolHandler;
import com.feign.framework.retry.RetryPolicy;
import com.feign.framework.retry.DefaultRetryPolicy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Dynamic proxy for Feign client interfaces.
 *
 * <h3>Execution pipeline</h3>
 * <pre>
 *   call → build request → interceptors(before) → load balancer
 *        → retry loop → protocolHandler → decoder → interceptors(after)
 *        → return typed object
 * </pre>
 */
public class FeignClientProxy implements InvocationHandler {

    private final FeignClientMetadata metadata;
    private final ProtocolHandler protocolHandler;
    private final List<FeignInterceptor> interceptors;
    private final LoadBalancer loadBalancer;
    private final RetryPolicy retryPolicy;
    private final Decoder decoder;
    private final int connectTimeout;
    private final int readTimeout;

    // ── constructors ──

    public FeignClientProxy(FeignClientMetadata metadata) {
        this(metadata, new ArrayList<>(), null, null, null);
    }

    public FeignClientProxy(FeignClientMetadata metadata,
                             List<FeignInterceptor> interceptors) {
        this(metadata, interceptors, null, null, null);
    }

    /**
     * Full constructor with all customization points.
     *
     * @param metadata       extracted from @FeignClient
     * @param interceptors   interceptor chain (sorted by order())
     * @param loadBalancer   custom load balancer, or null for default
     * @param protocolHandler custom protocol handler, or null for HTTP default
     * @param decoder        custom decoder, or null for Gson default
     */
    public FeignClientProxy(FeignClientMetadata metadata,
                             List<FeignInterceptor> interceptors,
                             LoadBalancer loadBalancer,
                             ProtocolHandler protocolHandler,
                             Decoder decoder) {
        this.metadata = metadata;

        this.interceptors = new ArrayList<>(interceptors);
        this.interceptors.sort(Comparator.comparingInt(FeignInterceptor::order));

        this.connectTimeout = metadata.getConnectTimeout() > 0 ? metadata.getConnectTimeout() : 5000;
        this.readTimeout = metadata.getReadTimeout() > 0 ? metadata.getReadTimeout() : 5000;

        this.protocolHandler = protocolHandler != null
            ? protocolHandler
            : createProtocolHandler(metadata.getUrl());

        this.loadBalancer = loadBalancer != null
            ? loadBalancer
            : createDefaultLoadBalancer();

        this.retryPolicy = createRetryPolicy();

        this.decoder = decoder != null ? decoder : new GsonDecoder();
    }

    public void addInterceptor(FeignInterceptor interceptor) {
        this.interceptors.add(interceptor);
        this.interceptors.sort(Comparator.comparingInt(FeignInterceptor::order));
    }

    // ── InvocationHandler ──

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }

        FeignMethod methodAnn = method.getAnnotation(FeignMethod.class);
        if (methodAnn == null) {
            throw new FeignException(
                "Method '" + method.getName() + "' is not annotated with @FeignMethod");
        }

        // 1. Build request
        Request request = buildRequest(methodAnn, method, args);

        // 2. Interceptors before
        for (FeignInterceptor i : interceptors) {
            request = i.beforeExecute(request);
        }

        // 3. Load balancer
        String targetUrl = resolveTargetUrl(request);

        // 4. Execute (sync or async) + decode
        Type returnType = method.getGenericReturnType();

        if (isAsyncReturnType(method)) {
            // Unwrap CompletableFuture<User> → User type for decoding
            Type innerType = unwrapAsyncReturnType(method);
            return executeAsyncWithRetry(request, targetUrl, innerType);
        } else {
            Response response = executeSyncWithRetry(request, targetUrl);

            // 5. Interceptors after (reverse)
            for (int i = interceptors.size() - 1; i >= 0; i--) {
                response = interceptors.get(i).afterExecute(response);
            }

            // 6. Decode
            return decode(response, returnType);
        }
    }

    // ── sync execution + retry ──

    private Response executeSyncWithRetry(Request request, String targetUrl) throws FeignException {
        FeignException lastException = null;
        int maxRetries = retryPolicy != null ? retryPolicy.getMaxRetries() : 0;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                Response resp = protocolHandler.execute(rebuildUrl(request, targetUrl));
                markLbComplete(targetUrl);
                return resp;
            } catch (FeignException e) {
                markLbComplete(targetUrl);
                lastException = e;
                notifyError(request, e);
                if (!shouldRetry(e, attempt)) throw e;
                sleep(retryPolicy.getRetryInterval());
            } catch (Exception e) {
                markLbComplete(targetUrl);
                lastException = new FeignException("Request failed: " + targetUrl, e);
                notifyError(request, lastException);
                if (!shouldRetry(e, attempt)) throw lastException;
                sleep(retryPolicy.getRetryInterval());
            }
        }
        throw lastException != null ? lastException
            : new FeignException("Max retries exceeded: " + targetUrl);
    }

    // ── async execution + retry ──

    private CompletableFuture<Object> executeAsyncWithRetry(
            Request request, String targetUrl, Type innerType) {
        return executeAsyncWithRetry(request, targetUrl, 0, innerType,
                new CompletableFuture<>());
    }

    private CompletableFuture<Object> executeAsyncWithRetry(
            Request request, String targetUrl, int attempt, Type innerType,
            CompletableFuture<Object> resultFuture) {

        protocolHandler.executeAsync(rebuildUrl(request, targetUrl))
            .thenAccept(response -> {
                // afterExecute (reverse)
                Response processed = response;
                for (int i = interceptors.size() - 1; i >= 0; i--) {
                    processed = interceptors.get(i).afterExecute(processed);
                }
                // decode
                try {
                    resultFuture.complete(decode(processed, innerType));
                } catch (Exception e) {
                    resultFuture.completeExceptionally(e);
                }
            })
            .exceptionally(throwable -> {
                Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                FeignException fe = (cause instanceof FeignException)
                    ? (FeignException) cause
                    : new FeignException("Async request failed: " + targetUrl, cause);

                notifyError(request, fe);

                if (shouldRetry(cause, attempt)) {
                    sleep(retryPolicy.getRetryInterval());
                    executeAsyncWithRetry(request, targetUrl, attempt + 1, innerType, resultFuture);
                } else {
                    resultFuture.completeExceptionally(fe);
                }
                return null;
            });

        return resultFuture;
    }

    // ── decode ──

    /**
     * Decode a Response into the target type.
     *
     * <pre>
     *   Response → return as-is
     *   String   → response.getBodyAsString()
     *   User     → decoder.decode(response, User.class)
     *   void     → null
     * </pre>
     */
    private Object decode(Response response, Type type) throws Exception {
        if (type == void.class || type == Void.class) {
            return null;
        }
        if (type == Response.class) {
            return response;
        }
        if (!response.successful()) {
            throw new FeignException(response.statusCode(), response.getUrl(),
                "Request failed: " + response.getBodyAsString());
        }
        return decoder.decode(response, type);
    }

    // ── request building ──

    private Request buildRequest(FeignMethod methodAnn, Method method, Object[] args) {
        String resolvedPath = resolvePath(methodAnn, method, args);
        String fullUrl = metadata.getUrl() + "/" + resolvedPath;
        Map<String, String> headers = parseHeaders(methodAnn);
        String body = extractBody(method, args);
        return Request.of(methodAnn.method(), fullUrl, headers, body, new HashMap<>());
    }

    private String resolvePath(FeignMethod methodAnn, Method method, Object[] args) {
        String[] segments = methodAnn.path();
        if (segments.length == 0) return method.getName();
        String path = String.join("/", segments);
        Map<String, String> pathParams = new HashMap<>();
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Path pathAnn = parameters[i].getAnnotation(Path.class);
            if (pathAnn != null && args != null && i < args.length && args[i] != null) {
                pathParams.put(pathAnn.value(), args[i].toString());
            }
        }
        for (Map.Entry<String, String> e : pathParams.entrySet()) {
            path = path.replace("{" + e.getKey() + "}", e.getValue());
        }
        return path;
    }

    private Map<String, String> parseHeaders(FeignMethod methodAnn) {
        Map<String, String> headers = new HashMap<>();
        for (String h : methodAnn.headers()) {
            int colon = h.indexOf(':');
            if (colon > 0) {
                headers.put(h.substring(0, colon).trim(), h.substring(colon + 1).trim());
            }
        }
        return headers;
    }

    private String extractBody(Method method, Object[] args) {
        if (args == null) return null;
        Parameter[] params = method.getParameters();
        for (int i = 0; i < params.length; i++) {
            if (params[i].getAnnotation(Path.class) == null
                && args[i] != null
                && !isPrimitiveOrWrapper(params[i].getType())) {
                return args[i].toString();
            }
        }
        return null;
    }

    private boolean isPrimitiveOrWrapper(Class<?> type) {
        return type.isPrimitive()
            || type == String.class
            || Number.class.isAssignableFrom(type)
            || Boolean.class.isAssignableFrom(type);
    }

    // ── load balancing ──

    private String resolveTargetUrl(Request request) {
        if (loadBalancer != null) {
            List<String> servers = getServers();
            if (servers != null && !servers.isEmpty()) {
                return loadBalancer.select(request, servers);
            }
        }
        return request.getUrl();
    }

    private List<String> getServers() {
        if (metadata.getUrl() != null && !metadata.getUrl().isEmpty()) {
            return Arrays.asList(metadata.getUrl());
        }
        return null;
    }

    // ── retry ──

    private boolean shouldRetry(Throwable e, int attempt) {
        if (retryPolicy == null) return false;
        if (e instanceof Exception) {
            return retryPolicy.canRetry((Exception) e, attempt);
        }
        return false;
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private void notifyError(Request request, FeignException e) {
        for (FeignInterceptor i : interceptors) {
            i.onError(request, e);
        }
    }

    private void markLbComplete(String url) {
        if (loadBalancer instanceof com.feign.framework.loadbalancer.LeastConnectionsLoadBalancer lb) {
            lb.markComplete(url);
        }
    }

    // ── helpers ──

    private Request rebuildUrl(Request original, String newUrl) {
        return Request.of(original.getMethod(), newUrl, original.getHeaders(),
            original.getBody() != null ? new String(original.getBody()) : null,
            original.getQueryParams());
    }

    private boolean isAsyncReturnType(Method method) {
        return CompletableFuture.class.isAssignableFrom(method.getReturnType());
    }

    /** Unwrap CompletableFuture&lt;T&gt; → T */
    private Type unwrapAsyncReturnType(Method method) {
        Type genericType = method.getGenericReturnType();
        if (genericType instanceof ParameterizedType) {
            Type[] args = ((ParameterizedType) genericType).getActualTypeArguments();
            if (args.length > 0) return args[0];
        }
        return Response.class;
    }

    @SuppressWarnings("unchecked")
    public <T> T createProxy(Class<T> interfaceClass) {
        return (T) Proxy.newProxyInstance(
            interfaceClass.getClassLoader(),
            new Class<?>[] { interfaceClass },
            this
        );
    }

    // ── defaults ──

    /**
     * Built-in protocol handlers. The proxy iterates these to find one whose
     * {@link ProtocolHandler#scheme()} matches the URL prefix.
     *
     * <p>To add a custom handler, instantiate it and pass into the constructor,
     * or call {@link #addProtocolHandler(ProtocolHandler)}.
     */
    private static final List<ProtocolHandler> builtinHandlers = new ArrayList<>();

    static {
        builtinHandlers.add(new HttpProtocolHandler(5000, 5000));  // scheme = "http"
        builtinHandlers.add(new GrpcProtocolHandler());             // scheme = "grpc"
        builtinHandlers.add(new WebSocketProtocolHandler());        // scheme = "ws"
    }

    /** Register a custom protocol handler for auto-detection via {@link ProtocolHandler#scheme()}. */
    public static void addProtocolHandler(ProtocolHandler handler) {
        builtinHandlers.add(handler);
    }

    /**
     * Auto-select ProtocolHandler by matching URL against each handler's {@code scheme()}.
     * TLS variants (https, wss) are auto-matched to their plain counterparts.
     */
    private ProtocolHandler createProtocolHandler(String url) {
        if (url == null || !url.contains("://")) {
            return builtinHandlers.get(0); // HTTP fallback
        }

        for (ProtocolHandler handler : builtinHandlers) {
            String scheme = handler.scheme();
            if (url.startsWith(scheme + "://") || url.startsWith(scheme + "s://")) {
                return handler;
            }
        }
        return builtinHandlers.get(0); // HTTP fallback
    }

    private LoadBalancer createDefaultLoadBalancer() {
        LoadBalancerType type = metadata.getLoadBalancerType();
        if (type == null) return null;
        return switch (type) {
            case ROUND_ROBIN -> new com.feign.framework.loadbalancer.RoundRobinLoadBalancer();
            case RANDOM      -> new com.feign.framework.loadbalancer.RandomLoadBalancer();
            case LEAST_CONNECTIONS -> new com.feign.framework.loadbalancer.LeastConnectionsLoadBalancer();
        };
    }

    private RetryPolicy createRetryPolicy() {
        DefaultRetryPolicy p = new DefaultRetryPolicy();
        p.setMaxRetries(Math.max(0, metadata.getMaxRetries()));
        p.setRetryInterval(Math.max(0, metadata.getRetryInterval()));
        return p;
    }
}
