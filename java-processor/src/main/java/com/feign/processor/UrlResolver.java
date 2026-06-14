package com.feign.processor;

import com.feign.framework.FeignException;
import com.feign.framework.discovery.ServiceDiscovery;
import com.feign.framework.loadbalancer.LoadBalancer;

import java.util.*;

/**
 * Resolves the base URL: annotation → ServiceDiscovery → LoadBalancer.
 */
class UrlResolver {

    private final String configUrl;
    private final String serviceName;
    private final ServiceDiscovery serviceDiscovery;
    private final LoadBalancer loadBalancer;

    UrlResolver(String configUrl, String serviceName,
                ServiceDiscovery serviceDiscovery, LoadBalancer loadBalancer) {
        this.configUrl = configUrl;
        this.serviceName = serviceName;
        this.serviceDiscovery = serviceDiscovery;
        this.loadBalancer = loadBalancer;
    }

    /** Resolve base URL. Call again on retry for different LB selection. */
    String resolve() {
        String url = configUrl;
        if (url != null && !url.isEmpty()) {
            // remove trailing path so we only get scheme+host+port
            int thirdSlash = url.indexOf("/", url.indexOf("://") + 3);
            if (thirdSlash > 0) url = url.substring(0, thirdSlash);
        }

        // Service discovery overrides annotation
        if (serviceDiscovery != null) {
            List<String> instances = serviceDiscovery.getInstances(serviceName);
            if (instances != null && !instances.isEmpty()) {
                url = instances.get(0);
            }
        }

        // Load balancer picks from available servers
        if (loadBalancer != null) {
            List<String> servers = getServers();
            if (servers != null && !servers.isEmpty()) {
                url = loadBalancer.select(null, servers);
            }
        }

        if (url == null || url.isEmpty()) {
            throw new FeignException("No URL for " + serviceName);
        }
        return url;
    }

    private List<String> getServers() {
        if (serviceDiscovery != null) {
            List<String> instances = serviceDiscovery.getInstances(serviceName);
            if (instances != null && !instances.isEmpty()) return instances;
        }
        if (configUrl != null && !configUrl.isEmpty()) return List.of(configUrl);
        return null;
    }
}
