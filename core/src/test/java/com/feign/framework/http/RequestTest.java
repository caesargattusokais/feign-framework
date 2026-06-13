package com.feign.framework.http;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Request} interface.
 *
 * @author Feign Framework Team
 * @version 1.0.0-SNAPSHOT
 */
class RequestTest {

    @Test
    void testGetMethodName() {
        Map<String, String> headers = new HashMap<>();
        Request request = createTestRequest(HttpMethod.GET, "http://example.com/api");

        assertEquals(HttpMethod.GET, request.getMethod());
        assertEquals("http://example.com/api", request.getUrl());
        assertNotNull(request.getHeaders());
    }

    @Test
    void testGetMethodPost() {
        Map<String, String> headers = new HashMap<>();
        Request request = createTestRequest(HttpMethod.POST, "http://example.com/api/users");

        assertEquals(HttpMethod.POST, request.getMethod());
    }

    @Test
    void testGetMethodPut() {
        Map<String, String> headers = new HashMap<>();
        Request request = createTestRequest(HttpMethod.PUT, "http://example.com/api/users/1");

        assertEquals(HttpMethod.PUT, request.getMethod());
    }

    @Test
    void testGetMethodDelete() {
        Map<String, String> headers = new HashMap<>();
        Request request = createTestRequest(HttpMethod.DELETE, "http://example.com/api/users/1");

        assertEquals(HttpMethod.DELETE, request.getMethod());
    }

    @Test
    void testGetMethodPatch() {
        Map<String, String> headers = new HashMap<>();
        Request request = createTestRequest(HttpMethod.PATCH, "http://example.com/api/users/1");

        assertEquals(HttpMethod.PATCH, request.getMethod());
    }

    @Test
    void testHasBodyReturnsTrue() {
        byte[] body = "test body".getBytes();
        Map<String, String> headers = new HashMap<>();
        Request request = new TestRequest(HttpMethod.POST, "http://example.com/api", headers, body);

        assertTrue(request.hasBody());
        assertEquals(body, request.getBody());
    }

    @Test
    void testHasBodyReturnsFalse() {
        Map<String, String> headers = new HashMap<>();
        Request request = new TestRequest(HttpMethod.GET, "http://example.com/api", headers, null);

        assertFalse(request.hasBody());
        assertEquals(0, request.getBody().length);
    }

    @Test
    void testIsSafeReturnsTrueForGet() {
        Map<String, String> headers = new HashMap<>();
        Request request = new TestRequest(HttpMethod.GET, "http://example.com/api", headers, null);

        assertTrue(request.isSafe());
    }

    @Test
    void testIsSafeReturnsTrueForHead() {
        Map<String, String> headers = new HashMap<>();
        Request request = new TestRequest(HttpMethod.HEAD, "http://example.com/api", headers, null);

        assertTrue(request.isSafe());
    }

    @Test
    void testIsSafeReturnsFalseForPost() {
        Map<String, String> headers = new HashMap<>();
        Request request = new TestRequest(HttpMethod.POST, "http://example.com/api", headers, null);

        assertFalse(request.isSafe());
    }

    @Test
    void testIsIdempotentReturnsTrueForGet() {
        Map<String, String> headers = new HashMap<>();
        Request request = new TestRequest(HttpMethod.GET, "http://example.com/api", headers, null);

        assertTrue(request.isIdempotent());
    }

    @Test
    void testIsIdempotentReturnsTrueForPut() {
        Map<String, String> headers = new HashMap<>();
        Request request = new TestRequest(HttpMethod.PUT, "http://example.com/api/users/1", headers, null);

        assertTrue(request.isIdempotent());
    }

    @Test
    void testIsIdempotentReturnsFalseForPost() {
        Map<String, String> headers = new HashMap<>();
        Request request = new TestRequest(HttpMethod.POST, "http://example.com/api/users", headers, null);

        assertFalse(request.isIdempotent());
    }

    @Test
    void testGetQueryParamsReturnsEmptyMapByDefault() {
        Map<String, String> headers = new HashMap<>();
        Request request = new TestRequest(HttpMethod.GET, "http://example.com/api", headers, null);

        assertNotNull(request.getQueryParams());
        assertTrue(request.getQueryParams().isEmpty());
    }

    // Test implementation for testing Request interface
    private static class TestRequest implements Request {
        private final HttpMethod method;
        private final String url;
        private final Map<String, String> headers;
        private final byte[] body;

        TestRequest(HttpMethod method, String url, Map<String, String> headers, byte[] body) {
            this.method = method;
            this.url = url;
            this.headers = headers != null ? headers : new HashMap<>();
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

    private static Request createTestRequest(HttpMethod method, String url) {
        return new TestRequest(method, url, new HashMap<>(), null);
    }
}
