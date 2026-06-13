package com.feign.framework.client;

import com.feign.framework.config.FeignConfig;
import com.feign.framework.loadbalancer.LoadBalancer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating Feign clients with load balancing support.
 */
public class FeignClientFactory {
    private final Map<String, LoadBalancer> loadBalancers = new ConcurrentHashMap<>();

    /**
     * Creates a new Feign client for the specified service.
     *
     * @param serviceName The name of the service
     * @param config The Feign configuration
     * @return A new HTTP client instance
     */
    public HttpClient createClient(String serviceName, FeignConfig config) {
        HttpClientConfig httpClientConfig = createHttpClientConfig(config);
        return new HttpClientImpl(httpClientConfig);
    }

    /**
     * Registers a load balancer for a specific service.
     *
     * @param serviceName The name of the service
     * @param loadBalancer The load balancer implementation
     */
    public void registerLoadBalancer(String serviceName, LoadBalancer loadBalancer) {
        loadBalancers.put(serviceName, loadBalancer);
    }

    /**
     * Gets the load balancer for a service.
     *
     * @param serviceName The name of the service
     * @return The load balancer, or null if not found
     */
    public LoadBalancer getLoadBalancer(String serviceName) {
        return loadBalancers.get(serviceName);
    }

    /**
     * Creates a HttpClientConfig from a FeignConfig.
     *
     * @param config The Feign configuration
     * @return The HTTP client configuration
     */
    private HttpClientConfig createHttpClientConfig(FeignConfig config) {
        HttpClientConfig httpClientConfig = new HttpClientConfig();
        if (config != null) {
            httpClientConfig.setConnectTimeout(config.getConnectTimeout());
            httpClientConfig.setReadTimeout(config.getReadTimeout());
        }
        return httpClientConfig;
    }
}
