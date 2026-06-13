package com.feign.framework.loadbalancer;

import com.feign.framework.http.Request;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Least-connections load balancer.
 *
 * <p>Selects the server with the fewest active connections.
 * Call {@link #markComplete(String)} when a request finishes
 * to decrement the counter for that server.
 */
public class LeastConnectionsLoadBalancer implements LoadBalancer {

    private final Set<String> servers = ConcurrentHashMap.newKeySet();
    private final Map<String, AtomicInteger> connections = new ConcurrentHashMap<>();

    @Override
    public String select(Request request, List<String> serverList) {
        if (serverList == null || serverList.isEmpty()) {
            throw new IllegalStateException("No servers available");
        }
        if (serverList.size() == 1) {
            increment(serverList.get(0));
            return serverList.get(0);
        }

        // Find server with fewest connections
        String selected = serverList.get(0);
        int min = connections.computeIfAbsent(selected, k -> new AtomicInteger(0)).get();

        for (int i = 1; i < serverList.size(); i++) {
            String server = serverList.get(i);
            int count = connections.computeIfAbsent(server, k -> new AtomicInteger(0)).get();
            if (count < min) {
                min = count;
                selected = server;
            }
        }

        increment(selected);
        return selected;
    }

    /**
     * Mark a request as complete for the given server URL, decrementing its connection count.
     */
    public void markComplete(String serverUrl) {
        AtomicInteger counter = connections.get(serverUrl);
        if (counter != null && counter.get() > 0) {
            counter.decrementAndGet();
        }
    }

    @Override
    public void addServer(String url) {
        servers.add(url);
        connections.putIfAbsent(url, new AtomicInteger(0));
    }

    @Override
    public void removeServer(String url) {
        servers.remove(url);
        connections.remove(url);
    }

    @Override
    public void reset() {
        servers.clear();
        connections.clear();
    }

    @Override
    public LoadBalancerType getType() {
        return LoadBalancerType.LEAST_CONNECTIONS;
    }

    private void increment(String server) {
        connections.computeIfAbsent(server, k -> new AtomicInteger(0)).incrementAndGet();
    }
}
