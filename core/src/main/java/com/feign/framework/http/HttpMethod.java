package com.feign.framework.http;

/**
 * HTTP methods supported by the framework.
 *
 * @author Feign Framework Team
 * @version 1.0.0-SNAPSHOT
 */
public enum HttpMethod {
    GET,
    POST,
    PUT,
    DELETE,
    PATCH,
    HEAD,
    OPTIONS,
    TRACE;


    /**
     * Checks if this method is idempotent (GET, PUT, DELETE, HEAD, OPTIONS).
     * @return true if idempotent, false otherwise
     */
    public boolean isIdempotent() {
        return this == GET || this == PUT || this == DELETE || this == HEAD || this == OPTIONS;
    }

    /**
     * Checks if this method is safe (GET, HEAD, OPTIONS, TRACE).
     * @return true if safe, false otherwise
     */
    public boolean isSafe() {
        return this == GET || this == HEAD || this == OPTIONS || this == TRACE;
    }
}
