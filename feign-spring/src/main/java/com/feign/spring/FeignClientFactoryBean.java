package com.feign.spring;

import com.feign.framework.codec.Decoder;
import com.feign.framework.interceptor.FeignInterceptor;
import com.feign.framework.loadbalancer.LoadBalancer;
import com.feign.framework.loadbalancer.LoadBalancerType;
import com.feign.framework.protocol.ProtocolHandler;
import com.feign.framework.protocol.HttpProtocolHandler;
import com.feign.framework.protocol.GrpcProtocolHandler;
import com.feign.framework.protocol.WebSocketProtocolHandler;
import com.feign.processor.FeignClientFactory;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring FactoryBean that creates a Feign client proxy for a @FeignClient interface.
 *
 * <p>Automatically resolves interceptors, decoder, load balancer, and protocol handler
 * from the Spring application context using the values configured in {@link FeignProperties}.
 */
public class FeignClientFactoryBean implements FactoryBean<Object>, BeanFactoryAware {

    private final Class<?> interfaceClass;
    private final String serviceName;
    private final String url;
    private BeanFactory beanFactory;

    public FeignClientFactoryBean(Class<?> interfaceClass, String serviceName, String url) {
        this.interfaceClass = interfaceClass;
        this.serviceName = serviceName;
        this.url = url;
    }

    @Override
    public void setBeanFactory(@NonNull BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @Override
    public Object getObject() {
        FeignProperties properties = beanFactory.getBean(FeignProperties.class);
        FeignProperties.ClientConfig config = properties.getMerged(serviceName);

        FeignClientFactory factory = new FeignClientFactory();

        // ── Encoder ──
        com.feign.framework.codec.Encoder encoder = resolveEncoder(config);
        factory.encoder(encoder);

        // ── Decoder ──
        Decoder decoder = resolveDecoder(config);
        factory.decoder(decoder);

        // ── ProtocolHandler ──
        ProtocolHandler handler = resolveProtocolHandler(config);
        factory.protocolHandler(handler);

        // ── LoadBalancer ──
        LoadBalancer lb = resolveLoadBalancer(config);
        factory.loadBalancer(lb);

        // ── ServiceDiscovery ──
        if (config.getDiscoveryBean() != null && beanFactory.containsBean(config.getDiscoveryBean())) {
            factory.serviceDiscovery(beanFactory.getBean(config.getDiscoveryBean(),
                com.feign.framework.discovery.ServiceDiscovery.class));
        }

        // ── Interceptors (by bean name) ──
        List<FeignInterceptor> interceptors = resolveInterceptors(config);
        for (FeignInterceptor interceptor : interceptors) {
            factory.addInterceptor(interceptor);
        }

        // ── URL (annotation value overridden by config if set) ──
        String targetUrl = config.getUrl() != null ? config.getUrl() : url;

        // ── Fallback (from Spring context) ──
        com.feign.framework.annotations.FeignClient ann =
            interfaceClass.getAnnotation(com.feign.framework.annotations.FeignClient.class);
        if (ann != null && ann.fallback() != Void.class) {
            if (beanFactory.containsBean(ann.fallback().getSimpleName())) {
                factory.fallbackInstance(beanFactory.getBean(ann.fallback()));
            } else {
                // Create fallback as Spring-managed bean (supports @Autowired inside fallback)
                Object fb = beanFactory.getBean(ann.fallback());
                factory.fallbackInstance(fb);
            }
        }

        return factory.build(interfaceClass, targetUrl);
    }

    @Override
    public Class<?> getObjectType() {
        return interfaceClass;
    }



    // ── resolvers ──

    private com.feign.framework.codec.Encoder resolveEncoder(FeignProperties.ClientConfig config) {
        if (config.getEncoder() != null && beanFactory.containsBean(config.getEncoder())) {
            return beanFactory.getBean(config.getEncoder(), com.feign.framework.codec.Encoder.class);
        }
        return new com.feign.framework.codec.GsonEncoder();
    }

    private Decoder resolveDecoder(FeignProperties.ClientConfig config) {
        Decoder defaultDecoder = beanFactory.getBean(Decoder.class);
        if (config.getDecoder() == null) return defaultDecoder;

        String name = config.getDecoder();
        if (beanFactory.containsBean(name)) {
            return beanFactory.getBean(name, Decoder.class);
        }
        return defaultDecoder;
    }

    private ProtocolHandler resolveProtocolHandler(FeignProperties.ClientConfig config) {
        // Custom bean first
        if (config.getProtocolHandlerBean() != null
                && beanFactory.containsBean(config.getProtocolHandlerBean())) {
            return beanFactory.getBean(config.getProtocolHandlerBean(), ProtocolHandler.class);
        }

        // Auto-detect from scheme
        String protocol = config.getProtocol();
        if (protocol != null) {
            switch (protocol.toLowerCase()) {
                case "grpc": return new GrpcProtocolHandler();
                case "ws":
                case "wss": return new WebSocketProtocolHandler();
            }
        }

        // Default HTTP with pool config
        int connect = config.getConnectTimeout() != null ? config.getConnectTimeout() : 5000;
        int read = config.getReadTimeout() != null ? config.getReadTimeout() : 10000;
        FeignProperties.ClientConfig.ConnectionPool pool = config.getConnectionPool();
        if (pool != null && pool.getMaxTotal() != null && pool.getMaxPerRoute() != null) {
            return new HttpProtocolHandler(connect, read, pool.getMaxTotal(), pool.getMaxPerRoute());
        }
        return new HttpProtocolHandler(connect, read);
    }

    private LoadBalancer resolveLoadBalancer(FeignProperties.ClientConfig config) {
        // Custom bean
        if (config.getLoadBalancerBean() != null
                && beanFactory.containsBean(config.getLoadBalancerBean())) {
            return beanFactory.getBean(config.getLoadBalancerBean(), LoadBalancer.class);
        }

        LoadBalancerType type = LoadBalancerType.ROUND_ROBIN;
        if (config.getLoadBalancer() != null) {
            try {
                type = LoadBalancerType.valueOf(config.getLoadBalancer().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        return switch (type) {
            case ROUND_ROBIN -> new com.feign.framework.loadbalancer.RoundRobinLoadBalancer();
            case LEAST_CONNECTIONS -> new com.feign.framework.loadbalancer.LeastConnectionsLoadBalancer();
            case RANDOM -> new com.feign.framework.loadbalancer.RandomLoadBalancer();
        };
    }

    private List<FeignInterceptor> resolveInterceptors(FeignProperties.ClientConfig config) {
        List<FeignInterceptor> result = new ArrayList<>();
        if (config.getInterceptors() == null) return result;

        for (String beanName : config.getInterceptors()) {
            if (beanFactory.containsBean(beanName)) {
                FeignInterceptor interceptor = beanFactory.getBean(beanName, FeignInterceptor.class);
                result.add(interceptor);
            }
        }
        return result;
    }
}
