package com.feign.framework.loadbalancer;

/**
 * Load balancing strategies supported by the framework.
 *
 * @author Feign Framework Team
 * @version 1.0.0-SNAPSHOT
 */
public enum LoadBalancerType {
    /**
     * Round-robin load balancing strategy.
     * Distributes requests sequentially among available servers.
     */
    ROUND_ROBIN,

    /**
     * Random load balancing strategy.
     * Distributes requests randomly among available servers.
     */
    RANDOM,

    /**
     * Least connections load balancing strategy.
     * Distributes requests to the server with the fewest active connections.
     */
    LEAST_CONNECTIONS
}
