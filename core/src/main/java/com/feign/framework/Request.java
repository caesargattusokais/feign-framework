package com.feign.framework;

/**
 * Abstraction for HTTP requests.
 *
 * @author Feign Framework Team
 * @version 1.0.0-SNAPSHOT
 */
public interface Request {
    /**
     * Gets the HTTP method (GET, POST, PUT, DELETE, etc.)
     * @return the HTTP method
     */
    String getMethod();

    /**
     * Gets the request URL
     * @return the request URL
     */
    String getUrl();
}
