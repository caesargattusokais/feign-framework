package com.feign.framework.loadbalancer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class RandomLoadBalancerTest {

    private RandomLoadBalancer balancer;

    @BeforeEach
    void setUp() {
        balancer = new RandomLoadBalancer();
    }

    @Test
    void testSelectFromServerList() {
        List<String> servers = Arrays.asList(
                "http://server1:8080",
                "http://server2:8080",
                "http://server3:8080"
        );

        String server = balancer.select(null, servers);
        assertNotNull(server);
        assertTrue(server.contains("server1") ||
                   server.contains("server2") ||
                   server.contains("server3"));
    }

    @Test
    void testSelectWithEmptyServers() {
        assertThrows(IllegalStateException.class, () -> {
            balancer.select(null, Arrays.asList());
        });
    }

    @Test
    void testSelectReturnsAllServers() {
        List<String> servers = Arrays.asList(
                "http://server1:8080",
                "http://server2:8080",
                "http://server3:8080"
        );

        Set<String> selectedServers = new HashSet<>();
        for (int i = 0; i < 30; i++) {
            String server = balancer.select(null, servers);
            selectedServers.add(server);
        }

        assertEquals(3, selectedServers.size());
    }

    @Test
    void testAddAndRemoveServer() {
        balancer.addServer("http://server1:8080");
        balancer.addServer("http://server2:8080");
        balancer.removeServer("http://server1:8080");

        assertEquals(LoadBalancerType.RANDOM, balancer.getType());
    }

    @Test
    void testReset() {
        balancer.addServer("http://server1:8080");
        balancer.addServer("http://server2:8080");

        List<String> servers = Arrays.asList("http://server1:8080", "http://server2:8080");
        balancer.select(null, servers);
        balancer.select(null, servers);

        balancer.reset();
    }
}
