package com.feign.framework.circuit;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DefaultCircuitBreakerTest {

    @Test void testInitialStateClosed() {
        CircuitBreaker cb = new DefaultCircuitBreaker();
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
        assertTrue(cb.allowRequest());
    }

    @Test void testOpenAfterThreshold() {
        DefaultCircuitBreaker cb = new DefaultCircuitBreaker(3, 60_000, 30_000, 2);

        // 3 failures → OPEN
        for (int i = 0; i < 3; i++) {
            assertTrue(cb.allowRequest()); // still CLOSED
            cb.onFailure();
        }
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
        assertFalse(cb.allowRequest()); // fast-fail
    }

    @Test void testOpenToHalfOpenAfterCooldown() throws Exception {
        DefaultCircuitBreaker cb = new DefaultCircuitBreaker(1, 60_000, 100, 1);
        cb.onFailure();
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());

        Thread.sleep(150); // wait for cooldown
        assertTrue(cb.allowRequest()); // HALF_OPEN probe allowed
        assertEquals(CircuitBreaker.State.HALF_OPEN, cb.getState());
    }

    @Test void testHalfOpenSuccessCloses() {
        DefaultCircuitBreaker cb = new DefaultCircuitBreaker(1, 60_000, 0, 1);
        cb.onFailure();
        // Force transition: OPEN → HALF_OPEN (cooldown=0)
        assertTrue(cb.allowRequest());

        cb.onSuccess();
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
    }

    @Test void testHalfOpenFailureReopens() {
        DefaultCircuitBreaker cb = new DefaultCircuitBreaker(1, 60_000, 0, 1);
        cb.onFailure();
        assertTrue(cb.allowRequest()); // HALF_OPEN probe

        cb.onFailure(); // probe fails
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
    }

    @Test void testMultipleSuccessesToClose() {
        DefaultCircuitBreaker cb = new DefaultCircuitBreaker(1, 60_000, 0, 3);
        cb.onFailure();
        assertTrue(cb.allowRequest()); // HALF_OPEN

        cb.onSuccess();
        assertEquals(CircuitBreaker.State.HALF_OPEN, cb.getState()); // 1/3
        cb.onSuccess();
        assertEquals(CircuitBreaker.State.HALF_OPEN, cb.getState()); // 2/3
        cb.onSuccess();
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());   // 3/3 → CLOSED
    }

    @Test void testSlidingWindowReset() {
        DefaultCircuitBreaker cb = new DefaultCircuitBreaker(5, 100_000, 30_000, 2);
        // 4 failures, should remain CLOSED
        for (int i = 0; i < 4; i++) {
            cb.onFailure();
            assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
        }
        assertEquals(4, cb.getFailureCount());
    }

    @Test void testSuccessResetsFailureCount() {
        DefaultCircuitBreaker cb = new DefaultCircuitBreaker(3, 60_000, 30_000, 2);
        cb.onFailure();
        cb.onFailure();
        assertEquals(2, cb.getFailureCount());

        cb.onSuccess();
        assertEquals(0, cb.getFailureCount()); // reset on success
    }
}
