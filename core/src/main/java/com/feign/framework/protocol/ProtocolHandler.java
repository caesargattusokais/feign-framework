package com.feign.framework.protocol;

import com.feign.framework.http.Request;
import com.feign.framework.Response;
import java.util.concurrent.CompletableFuture;

/**
 * Protocol abstraction layer.
 * Allows the framework to support multiple protocols (HTTP, gRPC, WebSocket)
 * through a unified interface.
 *
 * <p>To add a new protocol, implement this interface and register it.
 */
public interface ProtocolHandler {

    /**
     * The protocol scheme this handler supports (e.g., "http", "https", "grpc", "ws").
     */
    String scheme();

    /**
     * Execute a request synchronously.
     */
    Response execute(Request request) throws Exception;

    /**
     * Execute a request asynchronously.
     */
    CompletableFuture<Response> executeAsync(Request request);

    /**
     * Check if the target URL is reachable via this protocol.
     */
    boolean isAvailable(String url);
}
