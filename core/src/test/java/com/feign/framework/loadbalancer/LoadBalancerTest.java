package com.feign.framework.loadbalancer;

import com.feign.framework.http.HttpMethod;
import com.feign.framework.http.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link LoadBalancer} interface.
 *
 * @author Feign Framework Team
 * @version 1.0.0-SNAPSHOT
 */
class LoadBalancerTest {

    private MockLoadBalancer loadBalancer;

    @BeforeEach
    void setUp() {
        loadBalancer = new MockLoadBalancer(LoadBalancerType.ROUND_ROBIN);
    }

    @Test
    void testSelectReturnsServerFromList() {
        List<String> servers = Arrays.asList("http://server1:8080", "http://server2:8080", "http://server3:8080");
        Request request = createTestRequest();

        String selected = loadBalancer.select(request, servers);

        assertNotNull(selected);
        assertTrue(servers.contains(selected));
    }

    @Test
    void testSelectReturnsNullWhenNoServers() {
        List<String> servers = Collections.emptyList();
        Request request = createTestRequest();

        String selected = loadBalancer.select(request, servers);

        assertNull(selected);
    }

    @Test
    void testAddServerAddsToServerList() {
        loadBalancer.addServer("http://server1:8080");

        Set<String> servers = loadBalancer.getServers();
        assertTrue(servers.contains("http://server1:8080"));
    }

    @Test
    void testRemoveServerRemovesFromList() {
        loadBalancer.addServer("http://server1:8080");
        loadBalancer.addServer("http://server2:8080");

        loadBalancer.removeServer("http://server1:8080");

        Set<String> servers = loadBalancer.getServers();
        assertFalse(servers.contains("http://server1:8080"));
        assertTrue(servers.contains("http://server2:8080"));
    }

    @Test
    void testRemoveServerDoesNothingIfNotFound() {
        loadBalancer.addServer("http://server1:8080");
        loadBalancer.removeServer("http://nonexistent:8080");

        Set<String> servers = loadBalancer.getServers();
        assertTrue(servers.contains("http://server1:8080"));
    }

    @Test
    void testResetClearsState() {
        loadBalancer.addServer("http://server1:8080");
        loadBalancer.addServer("http://server2:8080");

        loadBalancer.reset();

        Set<String> servers = loadBalancer.getServers();
        assertTrue(servers.isEmpty());
    }

    @Test
    void testGetTypeReturnsCorrectType() {
        assertEquals(LoadBalancerType.ROUND_ROBIN, loadBalancer.getType());
    }

    @Test
    void testRoundRobinSelectionAlternates() {
        List<String> servers = Arrays.asList("http://server1:8080", "http://server2:8080");
        Request request = createTestRequest();

        String first = loadBalancer.select(request, servers);
        String second = loadBalancer.select(request, servers);
        String third = loadBalancer.select(request, servers);

        // Round robin should cycle through servers
        assertEquals(first, second);
        assertNotEquals(first, third);
    }

    @Test
    void testRandomSelectionAlwaysReturnsValidServer() {
        List<String> servers = Arrays.asList("http://server1:8080", "http://server2:8080", "http://server3:8080");
        Request request = createTestRequest();

        for (int i = 0; i < 100; i++) {
            String selected = loadBalancer.select(request, servers);
            assertTrue(servers.contains(selected));
        }
    }

    // Test implementation for LoadBalancer
    private static class MockLoadBalancer implements LoadBalancer {
        private final LoadBalancerType type;
        private Set<String> servers;
        private int roundRobinIndex = 0;

        MockLoadBalancer(LoadBalancerType type) {
            this.type = type;
            this.servers = Set.of("http://server1:8080", "http://server2:8080", "http://server3:8080");
        }

        @Override
        public String select(Request request, List<String> servers) {
            if (servers == null || servers.isEmpty()) {
                return null;
            }

            if (type == LoadBalancerType.ROUND_ROBIN) {
                String selected = servers.get(roundRobinIndex % servers.size());
                roundRobinIndex++;
                return selected;
            } else if (type == LoadBalancerType.RANDOM) {
                int randomIndex = (int) (Math.random() * servers.size());
                return servers.get(randomIndex);
            } else if (type == LoadBalancerType.LEAST_CONNECTIONS) {
                // For testing, just return the first server as if it had the fewest connections
                return servers.get(0);
            }

            return servers.get(0);
        }

        @Override
        public void addServer(String server) {
            if (servers == null) {
                servers = Set.of(server);
            } else {
                Set<String> newServers = Set.copyOf(servers);
                newServers = new java.util.HashSet<>(newServers);
                newServers.add(server);
                servers = Set.copyOf(newServers);
            }
        }

        @Override
        public void removeServer(String server) {
            if (servers != null) {
                Set<String> newServers = Set.copyOf(servers);
                newServers.remove(server);
                servers = Set.copyOf(newServers);
            }
        }

        @Override
        public void reset() {
            roundRobinIndex = 0;
        }

        @Override
        public LoadBalancerType getType() {
            return type;
        }

        Set<String> getServers() {
            return servers;
        }
    }

    private static Request createTestRequest() {
        return new TestRequest(HttpMethod.GET, "http://example.com/api", Collections.emptyMap(), null);
    }

    private static class TestRequest implements Request {
        private final HttpMethod method;
        private final String url;
        private final Map<String, String> headers;
        private final byte[] body;

        TestRequest(HttpMethod method, String url, Map<String, String> headers, byte[] body) {
            this.method = method;
            this.url = url;
            this.headers = headers;
            this.body = body;
        }

        @Override
        public HttpMethod getMethod() {
            return method;
        }

        @Override
        public String getUrl() {
            return url;
        }

        @Override
        public Map<String, String> getHeaders() {
            return headers;
        }

        @Override
        public byte[] getBody() {
            return body;
        }
    }
}
