package com.feign.processor;

import com.feign.framework.FeignException;
import com.feign.framework.annotations.FeignClient;
import com.feign.framework.circuit.CircuitBreaker;
import com.feign.framework.codec.Decoder;
import com.feign.framework.codec.Encoder;
import com.feign.framework.discovery.ServiceDiscovery;
import com.feign.framework.interceptor.FeignInterceptor;
import com.feign.framework.loadbalancer.LoadBalancer;
import com.feign.framework.protocol.ProtocolHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Factory for creating Feign client proxies with full customization.
 *
 * <h3>Fully customized</h3>
 * <pre>{@code
 * UserService service = new FeignClientFactory()
 *     .decoder(new JacksonDecoder())
 *     .encoder(new JacksonEncoder())
 *     .protocolHandler(new HttpProtocolHandler(5000, 10000, 200, 20))
 *     .loadBalancer(new MyLoadBalancer())
 *     .serviceDiscovery(new NacosDiscovery())
 *     .addInterceptor(new AuthInterceptor(), 0)
 *     .build(UserService.class);
 * }</pre>
 */
public class FeignClientFactory {

    private final List<FeignInterceptor> interceptors = new ArrayList<>();
    private Decoder decoder;
    private Encoder encoder;
    private ProtocolHandler protocolHandler;
    private LoadBalancer loadBalancer;
    private ServiceDiscovery serviceDiscovery;

    public FeignClientFactory decoder(Decoder d)           { this.decoder = d; return this; }
    public FeignClientFactory encoder(Encoder e)           { this.encoder = e; return this; }
    public FeignClientFactory protocolHandler(ProtocolHandler h) { this.protocolHandler = h; return this; }
    public FeignClientFactory loadBalancer(LoadBalancer lb)      { this.loadBalancer = lb; return this; }
    public FeignClientFactory serviceDiscovery(ServiceDiscovery sd) { this.serviceDiscovery = sd; return this; }

    private CircuitBreaker circuitBreaker;
    public FeignClientFactory circuitBreaker(CircuitBreaker cb) { this.circuitBreaker = cb; return this; }

    private Object fallbackInstance;
    /** Set a pre-built fallback instance (e.g., from Spring context). */
    public FeignClientFactory fallbackInstance(Object instance) {
        this.fallbackInstance = instance; return this;
    }

    public FeignClientFactory addInterceptor(FeignInterceptor i, int order) {
        this.interceptors.add(new OrderedInterceptor(i, order));
        this.interceptors.sort(Comparator.comparingInt(FeignInterceptor::order));
        return this;
    }

    public FeignClientFactory addInterceptor(FeignInterceptor i) {
        this.interceptors.add(i);
        this.interceptors.sort(Comparator.comparingInt(FeignInterceptor::order));
        return this;
    }

    public static <T> T create(Class<T> c) { return new FeignClientFactory().build(c, ""); }
    public static <T> T create(String url, Class<T> c) { return new FeignClientFactory().build(c, url); }

    @SuppressWarnings("unchecked")
    public <T> T build(Class<T> c) { return build(c, ""); }

    @SuppressWarnings("unchecked")
    public <T> T build(Class<T> c, String urlOverride) {
        FeignClient ann = c.getAnnotation(FeignClient.class);
        if (ann == null) throw new FeignException(c.getName() + " not annotated with @FeignClient");

        String url = (urlOverride != null && !urlOverride.isEmpty()) ? urlOverride : ann.url();

        FeignClientMetadata metadata = new FeignClientMetadata(
            ann.name(), url, ann.loadBalancer(), ann.path(),
            ann.connectTimeout(), ann.readTimeout(),
            ann.maxRetries(), ann.retryInterval()
        );

        FeignClientProxy handler = new FeignClientProxy(
            metadata, interceptors, loadBalancer, protocolHandler,
            decoder, encoder, serviceDiscovery, circuitBreaker, ann.fallback());

        if (fallbackInstance != null) {
            handler.setFallbackInstance(fallbackInstance);
        }

        return handler.createProxy(c);
    }

    private static class OrderedInterceptor implements FeignInterceptor {
        private final FeignInterceptor delegate;
        private final int order;
        OrderedInterceptor(FeignInterceptor d, int o) { this.delegate = d; this.order = o; }
        @Override public int order() { return order; }
        @Override public com.feign.framework.http.Request beforeExecute(com.feign.framework.http.Request r) { return delegate.beforeExecute(r); }
        @Override public com.feign.framework.Response afterExecute(com.feign.framework.Response r) { return delegate.afterExecute(r); }
        @Override public void onError(com.feign.framework.http.Request r, FeignException e) { delegate.onError(r, e); }
    }
}
