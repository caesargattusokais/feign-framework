package com.feign.framework;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FeignException}.
 *
 * @author Feign Framework Team
 * @version 1.0.0-SNAPSHOT
 */
class FeignExceptionTest {

    @Test
    void testConstructorWithMessageOnly() {
        FeignException exception = new FeignException("Test exception");

        assertEquals("Test exception", exception.getMessage());
        assertEquals(-1, exception.getStatus());
        assertNull(exception.getMethod());
        assertNull(exception.getUrl());
    }

    @Test
    void testConstructorWithMessageAndCause() {
        Throwable cause = new RuntimeException("Root cause");
        FeignException exception = new FeignException("Test exception", cause);

        assertEquals("Test exception", exception.getMessage());
        assertEquals(-1, exception.getStatus());
        assertNull(exception.getMethod());
        assertNull(exception.getUrl());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void testConstructorWithStatusMethodAndUrl() {
        FeignException exception = new FeignException(404, "Not Found", "GET", "http://example.com/api/users/1");

        assertEquals("Not Found", exception.getMessage());
        assertEquals(404, exception.getStatus());
        assertEquals("GET", exception.getMethod());
        assertEquals("http://example.com/api/users/1", exception.getUrl());
    }

    @Test
    void testConstructorWithStatusMethodUrlAndCause() {
        Throwable cause = new IOException("Connection refused");
        FeignException exception = new FeignException(500, "Internal Server Error", "GET",
            "http://example.com/api", cause);

        assertEquals("Internal Server Error", exception.getMessage());
        assertEquals(500, exception.getStatus());
        assertEquals("GET", exception.getMethod());
        assertEquals("http://example.com/api", exception.getUrl());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void testClientErrorExceptionConstructor() {
        FeignException exception = new FeignException(404, "GET", "http://example.com/api/users/1");

        assertEquals(404, exception.getStatus());
        assertEquals("Not Found", exception.getMessage());
        assertEquals("GET", exception.getMethod());
        assertEquals("http://example.com/api/users/1", exception.getUrl());
    }

    @Test
    void testServerErrorExceptionConstructor() {
        FeignException exception = new FeignException(500, "GET", "http://example.com/api");

        assertEquals(500, exception.getStatus());
        assertEquals("Internal Server Error", exception.getMessage());
        assertEquals("GET", exception.getMethod());
        assertEquals("http://example.com/api", exception.getUrl());
    }

    @Test
    void testClientErrorExceptionConstructorWithMessage() {
        FeignException exception = new FeignException(403, "Forbidden access", "GET", "http://example.com/api");

        assertEquals(403, exception.getStatus());
        assertEquals("Forbidden access", exception.getMessage());
        assertEquals("GET", exception.getMethod());
        assertEquals("http://example.com/api", exception.getUrl());
    }

    @Test
    void testServerErrorExceptionConstructorWithMessage() {
        FeignException exception = new FeignException(502, "Bad Gateway", "POST", "http://example.com/api/submit");

        assertEquals(502, exception.getStatus());
        assertEquals("Bad Gateway", exception.getMessage());
        assertEquals("POST", exception.getMethod());
        assertEquals("http://example.com/api/submit", exception.getUrl());
    }

    @Test
    void testConstructorWithCauseOnly() {
        Throwable cause = new RuntimeException("Test cause");
        FeignException exception = new FeignException(cause);

        assertEquals("Test cause", exception.getMessage());
        assertEquals(-1, exception.getStatus());
        assertNull(exception.getMethod());
        assertNull(exception.getUrl());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void testConstructorWithIOException() {
        IOException ioException = new IOException("Connection timeout");
        FeignException exception = new FeignException(ioException);

        assertEquals("Connection timeout", exception.getMessage());
        assertEquals(-1, exception.getStatus());
        assertNull(exception.getMethod());
        assertNull(exception.getUrl());
        assertEquals(ioException, exception.getCause());
    }

    @Test
    void testStatusTextFor200() {
        FeignException exception = new FeignException(200, "OK", "GET", "http://example.com/api");
        assertEquals("OK", exception.getMessage());
    }

    @Test
    void testStatusTextFor404() {
        FeignException exception = new FeignException(404, "Not Found", "GET", "http://example.com/api/users/1");
        assertEquals("Not Found", exception.getMessage());
    }

    @Test
    void testStatusTextFor500() {
        FeignException exception = new FeignException(500, "Internal Server Error", "POST", "http://example.com/api");
        assertEquals("Internal Server Error", exception.getMessage());
    }

    @Test
    void testStatusTextForUnknown() {
        FeignException exception = new FeignException(999, "Unknown Status", "GET", "http://example.com/api");
        assertEquals("Unknown Status", exception.getMessage());
    }

    @Test
    void testStatusTextFor301() {
        FeignException exception = new FeignException(301, "Moved Permanently", "GET", "http://example.com/api");
        assertEquals("Moved Permanently", exception.getMessage());
    }

    @Test
    void testStatusTextFor429() {
        FeignException exception = new FeignException(429, "Too Many Requests", "GET", "http://example.com/api");
        assertEquals("Too Many Requests", exception.getMessage());
    }

    @Test
    void testStatusTextFor502() {
        FeignException exception = new FeignException(502, "Bad Gateway", "POST", "http://example.com/api");
        assertEquals("Bad Gateway", exception.getMessage());
    }

    @Test
    void testToStringWithAllFields() {
        FeignException exception = new FeignException(404, "Not Found", "GET", "http://example.com/api/users/1");

        String str = exception.toString();
        assertTrue(str.contains("FeignException"));
        assertTrue(str.contains("Not Found"));
        assertTrue(str.contains("GET"));
        assertTrue(str.contains("http://example.com/api/users/1"));
        assertTrue(str.contains("Status: 404 Not Found"));
    }

    @Test
    void testToStringWithStatus() {
        FeignException exception = new FeignException(404, "GET", "http://example.com/api/users/1");

        String str = exception.toString();
        assertTrue(str.contains("FeignException"));
        assertTrue(str.contains("Status: 404 Not Found"));
    }

    @Test
    void testToStringWithMessageOnly() {
        FeignException exception = new FeignException("Test exception");

        String str = exception.toString();
        assertTrue(str.contains("FeignException"));
        assertTrue(str.contains("Test exception"));
    }

    @Test
    void testToStringWithMethodAndUrl() {
        FeignException exception = new FeignException("Test exception", new RuntimeException("cause"));

        String str = exception.toString();
        assertTrue(str.contains("FeignException"));
        assertTrue(str.contains("Test exception"));
    }

    @Test
    void testToStringWithMessageAndMethodAndUrl() {
        FeignException exception = new FeignException(200, "OK", "GET", "http://example.com/api");

        String str = exception.toString();
        assertTrue(str.contains("FeignException"));
        assertTrue(str.contains("OK"));
        assertTrue(str.contains("GET"));
        assertTrue(str.contains("http://example.com/api"));
    }

    @Test
    void testExceptionIsRuntimeException() {
        FeignException exception = new FeignException("Test exception");
        assertTrue(exception instanceof RuntimeException);
    }

    @Test
    void testGetStatusReturnsMinusOneForMessageOnlyException() {
        FeignException exception = new FeignException("Test exception");
        assertEquals(-1, exception.getStatus());
    }

    @Test
    void testGetMethodReturnsNullForMessageOnlyException() {
        FeignException exception = new FeignException("Test exception");
        assertNull(exception.getMethod());
    }

    @Test
    void testGetUrlReturnsNullForMessageOnlyException() {
        FeignException exception = new FeignException("Test exception");
        assertNull(exception.getUrl());
    }

    @Test
    void testMultipleExceptionsAreDifferentObjects() {
        FeignException exception1 = new FeignException(404, "GET", "http://example.com/api");
        FeignException exception2 = new FeignException(404, "GET", "http://example.com/api");

        assertNotSame(exception1, exception2);
        assertEquals(exception1, exception2);
    }
}
