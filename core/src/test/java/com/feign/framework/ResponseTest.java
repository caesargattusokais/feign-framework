package com.feign.framework;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Response} interface.
 *
 * @author Feign Framework Team
 * @version 1.0.0-SNAPSHOT
 */
public class ResponseTest {

    @Test
    void testSuccessfulReturnsTrueFor200() {
        Map<String, String> headers = new HashMap<>();
        Response response = new TestResponse(200, "OK", headers, "Success body".getBytes());

        assertTrue(response.successful());
        assertEquals(200, response.statusCode());
        assertEquals("OK", response.statusText());
    }

    @Test
    void testSuccessfulReturnsTrueFor204() {
        Map<String, String> headers = new HashMap<>();
        Response response = new TestResponse(204, "No Content", headers, null);

        assertTrue(response.successful());
        assertEquals(204, response.statusCode());
    }

    @Test
    void testSuccessfulReturnsFalseFor300() {
        Map<String, String> headers = new HashMap<>();
        Response response = new TestResponse(301, "Moved Permanently", headers, null);

        assertFalse(response.successful());
    }

    @Test
    void testSuccessfulReturnsFalseFor400() {
        Map<String, String> headers = new HashMap<>();
        Response response = new TestResponse(400, "Bad Request", headers, null);

        assertFalse(response.successful());
    }

    @Test
    void testSuccessfulReturnsFalseFor500() {
        Map<String, String> headers = new HashMap<>();
        Response response = new TestResponse(500, "Internal Server Error", headers, null);

        assertFalse(response.successful());
    }

    @Test
    void testClientErrorReturnsTrueFor400() {
        Map<String, String> headers = new HashMap<>();
        Response response = new TestResponse(404, "Not Found", headers, null);

        assertTrue(response.clientError());
        assertFalse(response.serverError());
    }

    @Test
    void testClientErrorReturnsTrueFor429() {
        Map<String, String> headers = new HashMap<>();
        Response response = new TestResponse(429, "Too Many Requests", headers, null);

        assertTrue(response.clientError());
    }

    @Test
    void testClientErrorReturnsFalseFor500() {
        Map<String, String> headers = new HashMap<>();
        Response response = new TestResponse(500, "Internal Server Error", headers, null);

        assertFalse(response.clientError());
    }

    @Test
    void testServerErrorReturnsTrueFor500() {
        Map<String, String> headers = new HashMap<>();
        Response response = new TestResponse(503, "Service Unavailable", headers, null);

        assertTrue(response.serverError());
    }

    @Test
    void testServerErrorReturnsTrueFor502() {
        Map<String, String> headers = new HashMap<>();
        Response response = new TestResponse(502, "Bad Gateway", headers, null);

        assertTrue(response.serverError());
    }

    @Test
    void testServerErrorReturnsFalseFor400() {
        Map<String, String> headers = new HashMap<>();
        Response response = new TestResponse(400, "Bad Request", headers, null);

        assertFalse(response.serverError());
    }

    @Test
    void testIsRedirectReturnsTrueFor301() {
        Map<String, String> headers = new HashMap<>();
        Response response = new TestResponse(301, "Moved Permanently", headers, null);

        assertTrue(response.isRedirect());
        assertFalse(response.successful());
    }

    @Test
    void testIsRedirectReturnsTrueFor302() {
        Map<String, String> headers = new HashMap<>();
        Response response = new TestResponse(302, "Found", headers, null);

        assertTrue(response.isRedirect());
    }

    @Test
    void testIsRedirectReturnsFalseFor200() {
        Map<String, String> headers = new HashMap<>();
        Response response = new TestResponse(200, "OK", headers, null);

        assertFalse(response.isRedirect());
    }

    @Test
    void testGetBodyReturnsEmptyByteArrayWhenNull() {
        Map<String, String> headers = new HashMap<>();
        Response response = new TestResponse(200, "OK", headers, null);

        assertNotNull(response.body());
        assertEquals(0, response.body().length);
    }

    @Test
    void testGetBodyReturnsCorrectBytes() {
        Map<String, String> headers = new HashMap<>();
        String expectedBody = "Test response body";
        Response response = new TestResponse(200, "OK", headers, expectedBody.getBytes());

        assertArrayEquals(expectedBody.getBytes(), response.body());
    }

    @Test
    void testGetBodyAsStringReturnsCorrectString() {
        Map<String, String> headers = new HashMap<>();
        String expectedBody = "Test response body";
        Response response = new TestResponse(200, "OK", headers, expectedBody.getBytes());

        assertEquals(expectedBody, response.getBodyAsString());
    }

    @Test
    void testGetHeadersReturnsImmutableMap() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer token");

        Response response = new TestResponse(200, "OK", headers, null);

        // Verify the map is not modifiable
        assertThrows(UnsupportedOperationException.class, () -> {
            response.headers().put("New-Header", "value");
        });
    }

    // Test implementation for testing Response interface
        public record TestResponse(int statusCode, String statusText, Map<String, String> headers,
                                   byte[] body) implements Response {
            public TestResponse(int statusCode, String statusText, Map<String, String> headers, byte[] body) {
                this.statusCode = statusCode;
                this.statusText = statusText;
                this.headers = headers != null ? new HashMap<>(headers) : new HashMap<>();
                this.body = body;
            }

            @Override
            public String getUrl() {
                return "";
            }

            @Override
            public Map<String, String> headers() {
                return Collections.unmodifiableMap(headers);
            }

            @Override
            public byte[] body() {
                return body != null ? body : new byte[0];
            }

            @Override
            public String getBodyAsString() {
                return Response.super.getBodyAsString();
            }

            @Override
            public boolean successful() {
                return Response.super.successful();
            }

            @Override
            public boolean clientError() {
                return Response.super.clientError();
            }

            @Override
            public boolean serverError() {
                return Response.super.serverError();
            }

            @Override
            public boolean isRedirect() {
                return Response.super.isRedirect();
            }
        }
}
