package com.feign.framework.loadbalancer;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;

class LeastConnectionsLoadBalancerTest {

    @Test void testSelectReturnsServer() {
        LeastConnectionsLoadBalancer lb = new LeastConnectionsLoadBalancer();
        List<String> servers = Arrays.asList("http://s1:8080", "http://s2:8080");
        String selected = lb.select(null, servers);
        assertNotNull(selected);
        assertTrue(servers.contains(selected));
    }

    @Test void testSelectLeastBusyServer() {
        LeastConnectionsLoadBalancer lb = new LeastConnectionsLoadBalancer();
        List<String> servers = Arrays.asList("http://s1:8080", "http://s2:8080", "http://s3:8080");

        // Make s1 busy (2 connections)
        lb.select(null, servers); // s1:1
        lb.select(null, servers); // s1:2

        // Now mark one s1 connection complete
        lb.markComplete("http://s1:8080"); // s1:1

        // Select a few more times — s2/s3 should be preferred since s1 has more connections
        String s = lb.select(null, servers);
        assertTrue(s.contains("s2") || s.contains("s3")); // should avoid s1
    }

    @Test void testMarkComplete() {
        LeastConnectionsLoadBalancer lb = new LeastConnectionsLoadBalancer();
        List<String> servers = List.of("http://s1:8080");
        lb.select(null, servers); // +1
        lb.markComplete("http://s1:8080"); // -1

        // Should not throw, counter back to 0
        String s = lb.select(null, servers);
        assertNotNull(s);
    }

    @Test void testEmptyServers() {
        LeastConnectionsLoadBalancer lb = new LeastConnectionsLoadBalancer();
        assertThrows(IllegalStateException.class, () -> lb.select(null, List.of()));
    }

    @Test void testReset() {
        LeastConnectionsLoadBalancer lb = new LeastConnectionsLoadBalancer();
        lb.addServer("http://s1:8080");
        lb.select(null, List.of("http://s1:8080"));
        lb.reset();
        assertThrows(IllegalStateException.class, () -> lb.select(null, List.of()));
    }

    @Test void testGetType() {
        assertEquals(LoadBalancerType.LEAST_CONNECTIONS,
            new LeastConnectionsLoadBalancer().getType());
    }
}
