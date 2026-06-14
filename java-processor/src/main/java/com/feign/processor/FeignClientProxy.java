package com.feign.processor;

import com.feign.framework.FeignException;
import com.feign.framework.Response;
import com.feign.framework.annotations.FeignMethod;
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

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Thin orchestrator for @FeignClient proxy invocation.
 *
 * <pre>
 * invoke → RequestBuilder.build → interceptors(before) → UrlResolver
 *        → RetryExecutor → decode → interceptors(after) → return
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
    private Object fallbackInstance;

    private final RequestBuilder requestBuilder;
    private final UrlResolver urlResolver;
    private final RetryExecutor retryExecutor;

    static final List<ProtocolHandler> builtinHandlers = new ArrayList<>();
    static { builtinHandlers.add(new HttpProtocolHandler(5000,5000)); builtinHandlers.add(new GrpcProtocolHandler()); builtinHandlers.add(new WebSocketProtocolHandler()); }
    public static void addProtocolHandler(ProtocolHandler h) { builtinHandlers.add(h); }

    public FeignClientProxy(FeignClientMetadata metadata) {
        this(metadata, List.of(), null, null, null, null, null, null, Void.class);
    }

    public FeignClientProxy(FeignClientMetadata metadata, List<FeignInterceptor> interceptors,
                             LoadBalancer loadBalancer, ProtocolHandler protocolHandler,
                             Decoder decoder, Encoder encoder,
                             ServiceDiscovery serviceDiscovery, CircuitBreaker circuitBreaker,
                             Class<?> fallbackClass) {
        this.metadata = metadata;
        this.interceptors = new ArrayList<>(interceptors);
        this.interceptors.sort(Comparator.comparingInt(FeignInterceptor::order));
        this.protocolHandler = protocolHandler != null ? protocolHandler : selectProtocol(metadata.getUrl());
        this.loadBalancer   = loadBalancer != null ? loadBalancer : defaultLb();
        this.retryPolicy    = defaultRetry();
        this.decoder        = decoder != null ? decoder : new GsonDecoder();
        this.encoder        = encoder != null ? encoder : new GsonEncoder();
        this.serviceDiscovery = serviceDiscovery;
        this.circuitBreaker   = circuitBreaker;
        this.fallbackClass    = fallbackClass != null && fallbackClass != Void.class ? fallbackClass : null;

        this.requestBuilder = new RequestBuilder(this.encoder);
        this.urlResolver    = new UrlResolver(metadata.getUrl(), metadata.getServiceName(), serviceDiscovery, loadBalancer);
        this.retryExecutor  = new RetryExecutor(this.protocolHandler, this.retryPolicy, this.circuitBreaker,
                                                this.loadBalancer, this.urlResolver, this.requestBuilder,
                                                this.interceptors);
    }

    public void setFallbackInstance(Object instance) { this.fallbackInstance = instance; }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) return method.invoke(this, args);
        FeignMethod ann = method.getAnnotation(FeignMethod.class);
        if (ann == null) throw new FeignException(method.getName() + " not @FeignMethod");

        try {
            if (circuitBreaker != null && !circuitBreaker.allowRequest()) {
                return fallbackOrThrow(method, args);
            }

            RequestBuilder.Result result = requestBuilder.build(ann, method, args);
            String baseUrl = urlResolver.resolve();
            String fullUrl = RequestBuilder.assembleUrl(baseUrl, result.path(), result.queryParams());

            Request request = Request.of(ann.method(), fullUrl, result.headers(), result.body(), result.queryParams());
            for (FeignInterceptor i : interceptors) request = i.beforeExecute(request);

            Type returnType = method.getGenericReturnType();
            if (isAsync(method)) {
                return executeAsync(request, fullUrl, unwrapAsync(method));
            } else {
                Response resp = retryExecutor.execute(request, fullUrl);
                for (int i = interceptors.size() - 1; i >= 0; i--) resp = interceptors.get(i).afterExecute(resp);
                return decode(resp, returnType);
            }
        } catch (Exception e) {
            return fallbackOrThrow(method, args);
        }
    }

    private Object fallbackOrThrow(Method method, Object[] args) throws Throwable {
        if (fallbackClass != null) {
            if (fallbackInstance == null) {
                synchronized (this) { if (fallbackInstance == null) fallbackInstance = fallbackClass.getDeclaredConstructor().newInstance(); }
            }
            return method.invoke(fallbackInstance, args);
        }
        throw (args.length > 0 && args[0] instanceof Throwable) ? (Throwable) args[0]
            : new FeignException("Request failed for " + metadata.getServiceName());
    }

    private Object decode(Response resp, Type type) throws Exception {
        if (type == void.class || type == Void.class) return null;
        if (type == Response.class) return resp;
        if (!resp.successful()) throw new FeignException(resp.statusCode(), resp.getUrl(), resp.getBodyAsString());
        if (type instanceof ParameterizedType pt && pt.getRawType() == com.feign.framework.FeignResponse.class) {
            Object body = decoder.decode(resp, pt.getActualTypeArguments()[0]);
            return new com.feign.framework.FeignResponse<>(body, resp.headers());
        }
        return decoder.decode(resp, type);
    }

    @SuppressWarnings("unchecked") public <T> T createProxy(Class<T> c) {
        return (T) Proxy.newProxyInstance(c.getClassLoader(), new Class<?>[]{c}, this);
    }

    // ── async (simplified) ──
    private CompletableFuture<Object> executeAsync(Request req, String url, Type innerType) {
        CompletableFuture<Object> f = new CompletableFuture<>();
        protocolHandler.executeAsync(rebuild(req, url)).thenAccept(resp -> {
            try { f.complete(decode(resp, innerType)); } catch (Exception e) { f.completeExceptionally(e); }
        }).exceptionally(t -> { f.completeExceptionally(t.getCause() != null ? t.getCause() : t); return null; });
        return f;
    }

    private static Request rebuild(Request o, String url) { return Request.of(o.getMethod(), url, o.getHeaders(), o.getBody(), o.getQueryParams()); }
    private boolean isAsync(Method m) { return CompletableFuture.class.isAssignableFrom(m.getReturnType()); }
    private Type unwrapAsync(Method m) {
        Type gt = m.getGenericReturnType();
        return gt instanceof ParameterizedType pt && pt.getActualTypeArguments().length > 0 ? pt.getActualTypeArguments()[0] : Response.class;
    }

    // ── defaults ──
    private ProtocolHandler selectProtocol(String url) {
        if (url == null || !url.contains("://")) return builtinHandlers.get(0);
        for (ProtocolHandler h : builtinHandlers) {
            String s = h.scheme();
            if (url.startsWith(s + "://") || url.startsWith(s + "s://")) return h;
        }
        return builtinHandlers.get(0);
    }
    private LoadBalancer defaultLb() {
        return switch (metadata.getLoadBalancerType()) {
            case ROUND_ROBIN -> new RoundRobinLoadBalancer();
            case RANDOM -> new RandomLoadBalancer();
            case LEAST_CONNECTIONS -> new LeastConnectionsLoadBalancer();
            case null -> null;
        };
    }
    private RetryPolicy defaultRetry() {
        DefaultRetryPolicy p = new DefaultRetryPolicy();
        p.setMaxRetries(Math.max(0, metadata.getMaxRetries()));
        p.setRetryInterval(Math.max(0, metadata.getRetryInterval()));
        return p;
    }
}
