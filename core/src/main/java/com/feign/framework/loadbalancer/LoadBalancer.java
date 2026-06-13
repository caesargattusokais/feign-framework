package com.feign.framework.loadbalancer;

import com.feign.framework.http.Request;
import java.util.List;

/**
 * Abstraction for server load balancing strategies.
 *
 * @author Feign Framework Team
 * @version 1.0.0-SNAPSHOT
 */
public interface LoadBalancer {

    /**
     * Selects a server from the available server list based on the load balancing strategy.
     *
     * @param request the HTTP request
     * @param servers the list of available servers
     * @return the selected server URL, or null if no servers are available
     */
    String select(Request request, List<String> servers);

    /**
     * Adds a server to the load balancer's server list.
     *
     * @param server the server URL to add
     */
    void addServer(String server);

    /**
     * Removes a server from the load balancer's server list.
     *
     * @param server the server URL to remove
     */
    void removeServer(String server);

    /**
     * Resets the load balancer's internal state (e.g., counters, round-robin index).
     */
    void reset();

    /**
     * Gets the load balancing strategy type.
     *
     * @return the load balancer type
     */
    LoadBalancerType getType();
}
