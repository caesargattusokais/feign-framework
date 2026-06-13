package com.feign.framework.loadbalancer;

import com.feign.framework.http.Request;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Random load balancer implementation.
 * Selects servers randomly from the available list.
 */
public class RandomLoadBalancer implements LoadBalancer {
    private final Set<String> servers = new HashSet<>();

    @Override
    public String select(Request request, List<String> serverList) {
        if (serverList == null || serverList.isEmpty()) {
            throw new IllegalStateException("No servers available");
        }

        // For Random, we use the provided server list
        String[] serverArray = serverList.toArray(new String[0]);
        int randomIndex = ThreadLocalRandom.current().nextInt(serverArray.length);
        return serverArray[randomIndex];
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
    }

    @Override
    public com.feign.framework.loadbalancer.LoadBalancerType getType() {
        return LoadBalancerType.RANDOM;
    }
}
