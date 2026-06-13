package com.feign.processor;

import com.feign.framework.FeignException;
import com.feign.framework.annotations.FeignClient;
import com.feign.framework.codec.Decoder;
import com.feign.framework.interceptor.FeignInterceptor;
import com.feign.framework.loadbalancer.LoadBalancer;
import com.feign.framework.loadbalancer.LoadBalancerType;
import com.feign.framework.protocol.ProtocolHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Factory for creating Feign client proxies with full customization.
 *
 * <h3>Quick start</h3>
 * <pre>{@code
 * UserService service = FeignClientFactory.create(UserService.class);
 * }</pre>
 *
 * <h3>Fully customized</h3>
 * <pre>{@code
 * UserService service = new FeignClientFactory()
 *     .decoder(new JacksonDecoder())
 *     .protocolHandler(new HttpProtocolHandler(5000, 10000, 200, 20))
 *     .loadBalancer(new MyCustomLoadBalancer())
 *     .addInterceptor(new AuthInterceptor("token"), 0)    // order=0
 *     .addInterceptor(new LoggingInterceptor(), 10)        // order=10
 *     .build(UserService.class);
 * }</pre>
 */
public class FeignClientFactory {

    private final List<FeignInterceptor> interceptors = new ArrayList<>();
    private Decoder decoder;
    private ProtocolHandler protocolHandler;
    private LoadBalancer loadBalancer;

    // ── customization (fluent API) ──

    /** Set the response decoder (default: GsonDecoder). */
    public FeignClientFactory decoder(Decoder decoder) {
        this.decoder = decoder;
        return this;
    }

    /** Set the protocol handler (default: HttpProtocolHandler with pooling). */
    public FeignClientFactory protocolHandler(ProtocolHandler handler) {
        this.protocolHandler = handler;
        return this;
    }

    /** Set a custom load balancer (default: from @FeignClient annotation). */
    public FeignClientFactory loadBalancer(LoadBalancer lb) {
        this.loadBalancer = lb;
        return this;
    }

    /** Register an interceptor with explicit order. Lower runs first. */
    public FeignClientFactory addInterceptor(FeignInterceptor interceptor, int order) {
        // Wrap to enforce the specified order
        this.interceptors.add(new OrderedInterceptor(interceptor, order));
        this.interceptors.sort(Comparator.comparingInt(FeignInterceptor::order));
        return this;
    }

    /** Register an interceptor using its own order(). */
    public FeignClientFactory addInterceptor(FeignInterceptor interceptor) {
        this.interceptors.add(interceptor);
        this.interceptors.sort(Comparator.comparingInt(FeignInterceptor::order));
        return this;
    }

    // ── static convenience ──

    public static <T> T create(Class<T> interfaceClass) {
        return new FeignClientFactory().build(interfaceClass, "");
    }

    public static <T> T create(String urlOverride, Class<T> interfaceClass) {
        return new FeignClientFactory().build(interfaceClass, urlOverride);
    }

    // ── build ──

    @SuppressWarnings("unchecked")
    public <T> T build(Class<T> interfaceClass) {
        return build(interfaceClass, "");
    }

    @SuppressWarnings("unchecked")
    public <T> T build(Class<T> interfaceClass, String urlOverride) {
        FeignClient ann = interfaceClass.getAnnotation(FeignClient.class);
        if (ann == null) {
            throw new FeignException(
                "Interface " + interfaceClass.getName() + " is not annotated with @FeignClient");
        }

        String url = (urlOverride != null && !urlOverride.isEmpty()) ? urlOverride : ann.url();

        FeignClientMetadata metadata = new FeignClientMetadata(
            ann.name(), url, ann.loadBalancer(), ann.path(),
            ann.connectTimeout(), ann.readTimeout(),
            ann.maxRetries(), ann.retryInterval()
        );

        FeignClientProxy handler = new FeignClientProxy(
            metadata, interceptors, loadBalancer, protocolHandler, decoder);

        return handler.createProxy(interfaceClass);
    }

    // ── helper: wraps interceptor to override order ──

    private static class OrderedInterceptor implements FeignInterceptor {
        private final FeignInterceptor delegate;
        private final int order;

        OrderedInterceptor(FeignInterceptor delegate, int order) {
            this.delegate = delegate;
            this.order = order;
        }

        @Override public int order() { return order; }

        @Override public com.feign.framework.http.Request beforeExecute(
                com.feign.framework.http.Request request) {
            return delegate.beforeExecute(request);
        }

        @Override public com.feign.framework.Response afterExecute(
                com.feign.framework.Response response) {
            return delegate.afterExecute(response);
        }

        @Override public void onError(com.feign.framework.http.Request request,
                                       FeignException exception) {
            delegate.onError(request, exception);
        }
    }
}
