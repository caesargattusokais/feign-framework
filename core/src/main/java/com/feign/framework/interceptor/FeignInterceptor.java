package com.feign.framework.interceptor;

import com.feign.framework.http.Request;
import com.feign.framework.Response;
import com.feign.framework.FeignException;

/**
 * Interceptor for Feign client requests.
 * Users implement this to add custom logic around each remote call.
 *
 * <h3>Ordering</h3>
 * Interceptors are sorted by {@link #order()} (ascending) before execution.
 * Lower values execute earlier on {@code beforeExecute}, later on {@code afterExecute}.
 * Default order is 0.
 *
 * <h3>Example</h3>
 * <pre>{@code
 * // Auth runs first (order=0), Logging runs second (order=10)
 * class AuthInterceptor implements FeignInterceptor {
 *     public int order() { return 0; }
 *     public Request beforeExecute(Request req) { ... }
 * }
 * class LoggingInterceptor implements FeignInterceptor {
 *     public int order() { return 10; }
 *     public Request beforeExecute(Request req) { ... }
 * }
 * }</pre>
 */
public interface FeignInterceptor {

    /**
     * Execution order. Lower values = higher priority (run earlier).
     * Default is 0.
     */
    default int order() {
        return 0;
    }

    /**
     * Called before the request is executed.
     * Can modify the request (e.g., add auth headers, inject trace IDs).
     *
     * @param request the request about to be executed
     * @return the (possibly modified) request
     */
    default Request beforeExecute(Request request) {
        return request;
    }

    /**
     * Called after a successful response is received.
     * Can modify or inspect the response (e.g., log, collect metrics).
     * Executed in REVERSE order (higher order = earlier in after-phase).
     *
     * @param response the response received
     * @return the (possibly modified) response
     */
    default Response afterExecute(Response response) {
        return response;
    }

    /**
     * Called when an error occurs during execution.
     * Useful for error logging, alerting, or fallback logic.
     *
     * @param request   the request that failed
     * @param exception the exception that occurred
     */
    default void onError(Request request, FeignException exception) {
    }
}
