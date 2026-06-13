package com.feign.framework.config;

import com.feign.framework.client.HttpClient;
import com.feign.framework.http.HttpMethod;
import com.feign.framework.loadbalancer.LoadBalancer;
import com.feign.framework.retry.RetryPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FeignConfig}.
 *
 * @author Feign Framework Team
 * @version 1.0.0-SNAPSHOT
 */
class FeignConfigTest {

    private FeignConfig config;

    @BeforeEach
    void setUp() {
        config = new FeignConfig();
    }

    @Test
    void testDefaultConstructor() {
        assertNotNull(config.getHttpClient());
        assertNotNull(config.getLoadBalancer());
        assertNotNull(config.getRetryPolicy());
        assertNull(config.getBaseUrl());
        assertNotNull(config.getDefaultHeaders());
        assertNotNull(config.getDefaultQueryParams());
        assertNotNull(config.getAllowedMethods());
        assertTrue(config.isRetryEnabled());
        assertEquals(3, config.getMaxRetries());
        assertEquals(1000, config.getRetryInterval());
        assertEquals("ROUND_ROBIN", config.getRetryLoadBalancerType());
        assertEquals(5000, config.getConnectTimeout());
        assertEquals(10000, config.getReadTimeout());
        assertTrue(config.isFollowRedirects());
        assertFalse(config.isCompressRequests());
        assertFalse(config.isCompressResponses());
    }

    @Test
    void testConstructorWithBaseUrl() {
        config = new FeignConfig("http://example.com/api");

        assertEquals("http://example.com/api", config.getBaseUrl());
    }

    @Test
    void testWithHttpClient() {
        HttpClient mockClient = new MockHttpClient("TestClient");

        config.withHttpClient(mockClient);

        assertEquals(mockClient, config.getHttpClient());
        assertEquals("TestClient", config.getHttpClient().getName());
    }

    @Test
    void testWithLoadBalancer() {
        LoadBalancer mockLoadBalancer = new MockLoadBalancer();

        config.withLoadBalancer(mockLoadBalancer);

        assertEquals(mockLoadBalancer, config.getLoadBalancer());
    }

    @Test
    void testWithRetryPolicy() {
        RetryPolicy mockRetryPolicy = new MockRetryPolicy();

        config.withRetryPolicy(mockRetryPolicy);

        assertEquals(mockRetryPolicy, config.getRetryPolicy());
    }

    @Test
    void testWithBaseUrl() {
        config.withBaseUrl("https://api.example.com/v1");

        assertEquals("https://api.example.com/v1", config.getBaseUrl());
    }

    @Test
    void testWithDefaultHeader() {
        config.withDefaultHeader("Content-Type", "application/json");
        config.withDefaultHeader("Authorization", "Bearer token");

        Map<String, String> headers = config.getDefaultHeaders();
        assertEquals("application/json", headers.get("Content-Type"));
        assertEquals("Bearer token", headers.get("Authorization"));
    }

    @Test
    void testWithDefaultQueryParam() {
        config.withDefaultQueryParam("version", "1.0");
        config.withDefaultQueryParam("format", "json");

        Map<String, String> queryParams = config.getDefaultQueryParams();
        assertEquals("1.0", queryParams.get("version"));
        assertEquals("json", queryParams.get("format"));
    }

    @Test
    void testWithAllowedMethod() {
        config.withAllowedMethod(HttpMethod.POST);
        config.withAllowedMethod(HttpMethod.PUT);

        List<HttpMethod> methods = config.getAllowedMethods();
        assertEquals(2, methods.size());
        assertTrue(methods.contains(HttpMethod.POST));
        assertTrue(methods.contains(HttpMethod.PUT));
    }

    @Test
    void testWithRetryEnabled() {
        config.withRetryEnabled(false);

        assertFalse(config.isRetryEnabled());
    }

    @Test
    void testWithMaxRetries() {
        config.withMaxRetries(5);

        assertEquals(5, config.getMaxRetries());
    }

    @Test
    void testWithRetryInterval() {
        config.withRetryInterval(2000);

        assertEquals(2000, config.getRetryInterval());
    }

    @Test
    void testWithRetryLoadBalancerType() {
        config.withRetryLoadBalancerType("RANDOM");

        assertEquals("RANDOM", config.getRetryLoadBalancerType());
    }

    @Test
    void testWithConnectTimeout() {
        config.withConnectTimeout(10000);

        assertEquals(10000, config.getConnectTimeout());
    }

    @Test
    void testWithReadTimeout() {
        config.withReadTimeout(15000);

        assertEquals(15000, config.getReadTimeout());
    }

    @Test
    void testWithFollowRedirects() {
        config.withFollowRedirects(false);

        assertFalse(config.isFollowRedirects());
    }

    @Test
    void testWithCompressRequests() {
        config.withCompressRequests(true);

        assertTrue(config.isCompressRequests());
    }

    @Test
    void testWithCompressResponses() {
        config.withCompressResponses(true);

        assertTrue(config.isCompressResponses());
    }

    @Test
    void testHeadersAreImmutable() {
        config.withDefaultHeader("Content-Type", "application/json");
        Map<String, String> headers = config.getDefaultHeaders();

        assertThrows(UnsupportedOperationException.class, () -> {
            headers.put("New-Header", "value");
        });
    }

    @Test
    void testQueryParamsAreImmutable() {
        config.withDefaultQueryParam("version", "1.0");
        Map<String, String> queryParams = config.getDefaultQueryParams();

        assertThrows(UnsupportedOperationException.class, () -> {
            queryParams.put("new-param", "value");
        });
    }

    @Test
    void testMethodsAreImmutable() {
        config.withAllowedMethod(HttpMethod.GET);
        List<HttpMethod> methods = config.getAllowedMethods();

        assertThrows(UnsupportedOperationException.class, () -> {
            methods.add(HttpMethod.POST);
        });
    }

    @Test
    void testConfigWithAllOptions() {
        config.withBaseUrl("http://example.com/api")
            .withDefaultHeader("Content-Type", "application/json")
            .withDefaultHeader("Authorization", "Bearer token")
            .withAllowedMethod(HttpMethod.GET)
            .withAllowedMethod(HttpMethod.POST)
            .withRetryEnabled(false)
            .withMaxRetries(5)
            .withRetryInterval(2000)
            .withConnectTimeout(8000)
            .withReadTimeout(12000)
            .withFollowRedirects(false)
            .withCompressRequests(true)
            .withCompressResponses(true);

        assertEquals("http://example.com/api", config.getBaseUrl());
        assertEquals(2, config.getDefaultHeaders().size());
        assertEquals(2, config.getAllowedMethods().size());
        assertFalse(config.isRetryEnabled());
        assertEquals(5, config.getMaxRetries());
        assertEquals(2000, config.getRetryInterval());
        assertEquals(8000, config.getConnectTimeout());
        assertEquals(12000, config.getReadTimeout());
        assertFalse(config.isFollowRedirects());
        assertTrue(config.isCompressRequests());
        assertTrue(config.isCompressResponses());
    }

    @Test
    void testGettersReturnNewInstances() {
        config.withDefaultHeader("Content-Type", "application/json");

        Map<String, String> headers1 = config.getDefaultHeaders();
        Map<String, String> headers2 = config.getDefaultHeaders();

        // Modifying one should not affect the other
        headers1.put("New-Header", "value");
        assertNull(headers2.get("New-Header"));
    }

    // Test implementations
    private static class MockHttpClient implements HttpClient {
        private final String name;

        MockHttpClient(String name) {
            this.name = name;
        }

        @Override
        public com.feign.framework.Response execute(com.feign.framework.http.Request request) {
            return null;
        }

        @Override
        public java.util.concurrent.CompletableFuture<com.feign.framework.Response> executeAsync(
                com.feign.framework.http.Request request) {
            return null;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    private static class MockLoadBalancer implements LoadBalancer {
        @Override
        public String select(com.feign.framework.http.Request request, List<String> servers) {
            return null;
        }

        @Override
        public void addServer(String server) {
        }

        @Override
        public void removeServer(String server) {
        }

        @Override
        public void reset() {
        }

        @Override
        public com.feign.framework.loadbalancer.LoadBalancerType getType() {
            return null;
        }
    }

    private static class MockRetryPolicy implements RetryPolicy {
        @Override
        public boolean canRetry(Exception e, int retryCount) {
            return false;
        }

        @Override
        public int getMaxRetries() {
            return 3;
        }

        @Override
        public long getRetryInterval() {
            return 1000;
        }

        @Override
        public String getLoadBalancerType() {
            return "ROUND_ROBIN";
        }

        @Override
        public boolean isEnabled() {
            return true;
        }
    }
}
