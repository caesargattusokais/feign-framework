package com.feign.framework.http;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Abstraction for HTTP requests.
 *
 * @author Feign Framework Team
 * @version 1.0.0-SNAPSHOT
 */
public interface Request {

    /**
     * Creates a Request with a byte[] body (no encoding round-trip).
     */
    static Request of(HttpMethod method, String url, Map<String, String> headers,
                      byte[] body, Map<String, String> queryParams) {
        return new Request() {
            @Override public HttpMethod getMethod() { return method; }
            @Override public String getUrl() { return url; }
            @Override public Map<String, String> getHeaders() {
                return Collections.unmodifiableMap(new HashMap<>(headers != null ? headers : Map.of()));
            }
            @Override public byte[] getBody() { return body; }
            @Override public Map<String, String> getQueryParams() {
                return Collections.unmodifiableMap(new HashMap<>(queryParams != null ? queryParams : Map.of()));
            }
        };
    }

    /**
     * Creates a Request with a String body (convenience). Prefer byte[] for binary data.
     */
    static Request of(HttpMethod method, String url, Map<String, String> headers,
                      String body, Map<String, String> queryParams) {
        byte[] bodyBytes = body != null ? body.getBytes() : null;
        Map<String, String> headersCopy = headers != null
                ? Collections.unmodifiableMap(new HashMap<>(headers))
                : Collections.emptyMap();
        Map<String, String> queryCopy = queryParams != null
                ? Collections.unmodifiableMap(new HashMap<>(queryParams))
                : Collections.emptyMap();
        return new Request() {
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
                return headersCopy;
            }

            @Override
            public byte[] getBody() {
                return bodyBytes;
            }

            @Override
            public Map<String, String> getQueryParams() {
                return queryCopy;
            }
        };
    }

    /**
     * Gets the HTTP method (GET, POST, PUT, DELETE, PATCH, etc.)
     * @return the HTTP method
     */
    HttpMethod getMethod();

    /**
     * Gets the request URL
     * @return the request URL
     */
    String getUrl();

    /**
     * Gets the request headers
     * @return immutable map of headers (key-value pairs)
     */
    Map<String, String> getHeaders();

    /**
     * Gets the request body
     * @return the request body, or null if no body
     */
    byte[] getBody();

    /**
     * Gets the query parameters
     * @return immutable map of query parameters
     */
    default Map<String, String> getQueryParams() {
        return Collections.emptyMap();
    }

    /**
     * Checks if this request has a body
     * @return true if request has body, false otherwise
     */
    default boolean hasBody() {
        return getBody() != null && getBody().length > 0;
    }

    /**
     * Checks if this request is safe (GET, HEAD, OPTIONS, TRACE)
     * @return true if safe, false otherwise
     */
    default boolean isSafe() {
        return getMethod().isSafe();
    }

    /**
     * Checks if this request is idempotent (GET, PUT, DELETE, HEAD, OPTIONS)
     * @return true if idempotent, false otherwise
     */
    default boolean isIdempotent() {
        return getMethod().isIdempotent();
    }
}
