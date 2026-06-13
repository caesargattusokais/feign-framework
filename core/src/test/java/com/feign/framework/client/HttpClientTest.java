package com.feign.framework.client;

import com.feign.framework.ResponseTest;
import com.feign.framework.http.HttpMethod;
import com.feign.framework.http.Request;
import com.feign.framework.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HttpClient} interface.
 *
 * @author Feign Framework Team
 * @version 1.0.0-SNAPSHOT
 */
class HttpClientTest {

    private MockHttpClient httpClient;

    @BeforeEach
    void setUp() {
        httpClient = new MockHttpClient("TestClient");
    }

    @Test
    void testExecuteReturnsResponse() throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        Request request = new TestRequest(HttpMethod.GET, "http://example.com/api", headers, null);

        Response response = httpClient.execute(request);

        assertNotNull(response);
        assertEquals(200, response.statusCode());
        assertEquals("OK", response.statusText());
    }

    @Test
    void testExecuteAsyncReturnsCompletableFuture() throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        Request request = new TestRequest(HttpMethod.POST, "http://example.com/api", headers, null);

        CompletableFuture<Response> future = httpClient.executeAsync(request);

        assertNotNull(future);
        Response response = future.get();
        assertEquals(200, response.statusCode());
    }

    @Test
    void testExecuteAsyncThrowsException() {
        MockFailingHttpClient failingClient = new MockFailingHttpClient("FailingClient");

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        Request request = new TestRequest(HttpMethod.GET, "http://example.com/api", headers, null);

        CompletableFuture<Response> future = failingClient.executeAsync(request);

        ExecutionException exception = assertThrows(ExecutionException.class, future::get);
        assertNotNull(exception.getCause());
        assertEquals("Simulated failure", exception.getCause().getMessage());
    }

    @Test
    void testIsAvailableReturnsTrue() {
        assertTrue(httpClient.isAvailable());
    }

    @Test
    void testIsAvailableReturnsFalse() {
        MockUnavailableClient unavailableClient = new MockUnavailableClient("UnavailableClient");
        assertFalse(unavailableClient.isAvailable());
    }

    @Test
    void testGetNameReturnsClientName() {
        assertEquals("TestClient", httpClient.getName());
    }

    @Test
    void testExecuteWithNullRequestThrowsException() {
        assertThrows(NullPointerException.class, () -> httpClient.execute(null));
    }

    @Test
    void testExecuteAsyncWithNullRequestThrowsException() {
        assertThrows(NullPointerException.class, () -> httpClient.executeAsync(null));
    }

    // Test implementation for HttpClient
    private static class MockHttpClient implements HttpClient {
        private final String name;
        private boolean available = true;

        MockHttpClient(String name) {
            this.name = name;
        }

        @Override
        public Response execute(Request request) throws Exception {
            if (!available) {
                throw new IllegalStateException("Client not available");
            }
            return new ResponseTest.TestResponse(200, "OK", new HashMap<>(), null);
        }

        @Override
        public CompletableFuture<Response> executeAsync(Request request) throws Exception {
            return CompletableFuture.completedFuture(execute(request));
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    private static class MockFailingHttpClient implements HttpClient {
        private final String name;

        MockFailingHttpClient(String name) {
            this.name = name;
        }

        @Override
        public Response execute(Request request) {
            throw new RuntimeException("Simulated failure");
        }

        @Override
        public CompletableFuture<Response> executeAsync(Request request)  {
            return CompletableFuture.failedFuture(new RuntimeException("Simulated failure"));
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

    private static class MockUnavailableClient implements HttpClient {
        private final String name;
        private final boolean available;

        MockUnavailableClient(String name) {
            this.name = name;
            this.available = false;
        }

        @Override
        public Response execute(Request request) throws Exception {
            throw new IllegalStateException("Client not available");
        }

        @Override
        public CompletableFuture<Response> executeAsync(Request request) throws Exception {
            throw new IllegalStateException("Client not available");
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public String getName() {
            return name;
        }
    }

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
}
