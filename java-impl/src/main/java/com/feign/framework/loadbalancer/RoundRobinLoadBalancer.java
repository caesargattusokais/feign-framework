package com.feign.framework.loadbalancer;

import com.feign.framework.http.Request;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Round Robin load balancer implementation.
 * Distributes requests across servers in a circular order.
 */
public class RoundRobinLoadBalancer implements LoadBalancer {
    private final Queue<String> servers = new LinkedList<>();
    private int position = 0;

    @Override
    public String select(Request request, List<String> servers) {
        if (servers == null || servers.isEmpty()) {
            throw new IllegalStateException("No servers available");
        }

        // For RoundRobin, we need to use the passed server list
        // But we'll maintain state across calls
        String server = servers.get(position % servers.size());
        position++;
        return server;
    }

    @Override
    public void addServer(String url) {
        servers.add(url);
    }

    @Override
    public void removeServer(String url) {
        servers.remove(url);
    }

    @Override
    public void reset() {
        servers.clear();
        position = 0;
    }

    @Override
    public com.feign.framework.loadbalancer.LoadBalancerType getType() {
        return LoadBalancerType.ROUND_ROBIN;
    }
}
