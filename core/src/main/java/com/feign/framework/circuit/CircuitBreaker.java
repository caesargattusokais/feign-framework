package com.feign.framework.circuit;

/**
 * Three-state circuit breaker — prevents cascading failures.
 *
 * <pre>
 * CLOSED    → normal, count failures → exceed threshold → OPEN
 * OPEN      → fast-fail, no requests → wait timeout → HALF_OPEN
 * HALF_OPEN → one probe request → success → CLOSED
 *                               → failure → OPEN
 * </pre>
 */
public interface CircuitBreaker {

    enum State { CLOSED, OPEN, HALF_OPEN }

    /** Called before each request. Returns false if circuit is open (should fallback). */
    boolean allowRequest();

    /** Called on successful request. */
    void onSuccess();

    /** Called on failed request. */
    void onFailure();

    /** Current circuit state. */
    State getState();
}
