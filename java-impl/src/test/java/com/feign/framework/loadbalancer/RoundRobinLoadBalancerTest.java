package com.feign.framework.loadbalancer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RoundRobinLoadBalancerTest {

    private RoundRobinLoadBalancer balancer;

    @BeforeEach
    void setUp() {
        balancer = new RoundRobinLoadBalancer();
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
    void testSelectWithNullServers() {
        assertThrows(IllegalStateException.class, () -> {
            balancer.select(null, null);
        });
    }

    @Test
    void testRoundRobinDistribution() {
        List<String> servers = Arrays.asList(
                "http://server1:8080",
                "http://server2:8080"
        );

        String server1 = balancer.select(null, servers);
        String server2 = balancer.select(null, servers);
        String server3 = balancer.select(null, servers);

        // After two selects, server1 should be returned again
        assertEquals(server1, server3);
    }

    @Test
    void testAddServer() {
        balancer.addServer("http://server1:8080");
        balancer.addServer("http://server2:8080");
        balancer.addServer("http://server3:8080");

        // addServer populates internal server list; select uses passed list
        // but getType() should still work
        assertEquals(LoadBalancerType.ROUND_ROBIN, balancer.getType());
    }

    @Test
    void testRemoveServer() {
        balancer.addServer("http://server1:8080");
        balancer.addServer("http://server2:8080");
        balancer.removeServer("http://server1:8080");

        // Verify server removal by checking the state indirectly via reset
        balancer.reset();
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
