package com.feign.framework.client;

import com.feign.framework.http.Request;
import com.feign.framework.Response;

import java.util.concurrent.CompletableFuture;

/**
 * Abstraction for HTTP client operations.
 *
 * @author Feign Framework Team
 * @version 1.0.0-SNAPSHOT
 */
public interface HttpClient {

    /**
     * Synchronously executes an HTTP request.
     *
     * @param request the HTTP request to execute
     * @return the HTTP response
     * @throws Exception if the request fails
     */
    Response execute(Request request) throws Exception;

    /**
     * Asynchronously executes an HTTP request.
     *
     * @param request the HTTP request to execute
     * @return a CompletableFuture containing the HTTP response
     * @throws Exception if the request fails
     */
    CompletableFuture<Response> executeAsync(Request request) throws Exception;

    /**
     * Checks if the HTTP client is available and can handle requests.
     *
     * @return true if the client is available, false otherwise
     */
    boolean isAvailable();

    /**
     * Gets the name of the HTTP client implementation.
     *
     * @return the client name
     */
    String getName();
}
