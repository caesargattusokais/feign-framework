package com.feign.processor;

import com.feign.framework.loadbalancer.LoadBalancerType;

/**
 * Configuration metadata extracted from @FeignClient annotation.
 * Carries all settings needed by the proxy to configure load balancing,
 * retry, timeouts, and protocol handling.
 */
public class FeignClientMetadata {
    private final String serviceName;
    private final String url;
    private final LoadBalancerType loadBalancerType;
    private final String[] path;
    private final int connectTimeout;
    private final int readTimeout;
    private final int maxRetries;
    private final long retryInterval;

    /**
     * Full constructor with all configuration.
     */
    public FeignClientMetadata(String serviceName, String url,
                                LoadBalancerType loadBalancerType, String[] path,
                                int connectTimeout, int readTimeout,
                                int maxRetries, long retryInterval) {
        this.serviceName = serviceName;
        this.url = url;
        this.loadBalancerType = loadBalancerType;
        this.path = path != null ? path : new String[0];
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.maxRetries = maxRetries;
        this.retryInterval = retryInterval;
    }

    /**
     * Simplified constructor (backward compatible).
     */
    public FeignClientMetadata(String serviceName, String url,
                                String loadBalancerName, String[] path) {
        this(serviceName, url,
             parseLoadBalancerType(loadBalancerName), path,
             5000, 5000, 3, 1000L);
    }

    private static LoadBalancerType parseLoadBalancerType(String name) {
        if (name == null) return LoadBalancerType.ROUND_ROBIN;
        try {
            return LoadBalancerType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return LoadBalancerType.ROUND_ROBIN;
        }
    }

    // --- getters ---

    public String getServiceName() { return serviceName; }
    public String getUrl() { return url; }
    public LoadBalancerType getLoadBalancerType() { return loadBalancerType; }
    public String[] getPath() { return path; }
    public int getConnectTimeout() { return connectTimeout; }
    public int getReadTimeout() { return readTimeout; }
    public int getMaxRetries() { return maxRetries; }
    public long getRetryInterval() { return retryInterval; }
}
