package com.feign.framework.circuit;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Three-state circuit breaker.
 *
 * <h3>State diagram</h3>
 * <pre>
 *                  ┌─────────────────────────────┐
 *                  │         CLOSED               │
 *                  │  (normal, count failures)     │
 *                  └─────────────┬───────────────┘
 *                                │ failures ≥ threshold
 *                                ▼
 *                  ┌─────────────────────────────┐
 *                  │          OPEN                │
 *                  │  (fast-fail, no requests)    │
 *                  └─────────────┬───────────────┘
 *                                │ wait cooldownMs
 *                                ▼
 *                  ┌─────────────────────────────┐
 *                  │        HALF_OPEN             │
 *                  │  (allow ONE probe request)   │
 *                  └──────┬────────────┬─────────┘
 *                    probe success    probe fails
 *                         │               │
 *                         ▼               ▼
 *                      CLOSED           OPEN
 * </pre>
 *
 * <p>Example: 5 failures in 60s → OPEN, wait 30s → probe, 2 successes → CLOSED.
 */
public class DefaultCircuitBreaker implements CircuitBreaker {

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);

    // Failures in current window (sliding, resets after windowMs silence)
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicLong firstFailureTime = new AtomicLong(0);

    // When circuit opened (used for cooldown)
    private final AtomicLong openedAt = new AtomicLong(0);

    // In HALF_OPEN: how many consecutive successes needed to close
    private final AtomicInteger halfOpenSuccesses = new AtomicInteger(0);

    private final int failureThreshold;   // e.g. 5 failures
    private final long windowMs;          // e.g. 60_000ms
    private final long halfOpenAfterMs;   // e.g. 30_000ms
    private final int successThreshold;   // e.g. 2 successes to close

    public DefaultCircuitBreaker() {
        this(5, 60_000, 30_000, 2);
    }

    /**
     * @param failThreshold   open after this many failures in the window
     * @param windowMs        sliding window for counting failures
     * @param cooldownMs      wait this long before HALF_OPEN probe
     * @param succThreshold   consecutive successes in HALF_OPEN to close
     */
    public DefaultCircuitBreaker(int failThreshold, long windowMs,
                                  long cooldownMs, int succThreshold) {
        this.failureThreshold = failThreshold;
        this.windowMs = windowMs;
        this.halfOpenAfterMs = cooldownMs;
        this.successThreshold = succThreshold;
    }

    // ── CircuitBreaker API ──

    @Override
    public boolean allowRequest() {
        State s = state.get();

        if (s == State.CLOSED) {
            return true; // normal operation
        }

        if (s == State.OPEN) {
            long elapsed = System.currentTimeMillis() - openedAt.get();
            if (elapsed >= halfOpenAfterMs) {
                // Cooldown expired → transition to HALF_OPEN (one probe)
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    halfOpenSuccesses.set(0);
                    return true; // ← this is the probe
                }
                // CAS failed → another thread already transitioned, re-check
                return allowRequest();
            }
            return false; // still cooling down, fast-fail
        }

        // HALF_OPEN — only allow one probe at a time
        // (first call gets true, subsequent concurrent calls get false)
        return state.get() == State.HALF_OPEN;
    }

    @Override
    public void onSuccess() {
        State s = state.get();

        // Reset failure tracking
        failureCount.set(0);
        firstFailureTime.set(0);

        if (s == State.HALF_OPEN) {
            int successes = halfOpenSuccesses.incrementAndGet();
            if (successes >= successThreshold) {
                state.set(State.CLOSED);
                halfOpenSuccesses.set(0);
            }
        }
    }

    @Override
    public void onFailure() {
        long now = System.currentTimeMillis();

        // ── Sliding window: reset if first failure was outside window ──
        long first = firstFailureTime.get();
        if (first == 0) {
            firstFailureTime.compareAndSet(0, now);
        } else if (now - first > windowMs) {
            // Window expired — reset and start fresh
            firstFailureTime.set(now);
            failureCount.set(0);
        }

        int count = failureCount.incrementAndGet();

        // CLOSED → OPEN (too many failures)
        State s = state.get();
        if (s == State.CLOSED && count >= failureThreshold) {
            if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                openedAt.set(now);
            }
        }

        // HALF_OPEN probe failed → back to OPEN
        if (s == State.HALF_OPEN) {
            if (state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                openedAt.set(now);
                halfOpenSuccesses.set(0);
            }
        }
    }

    @Override
    public State getState() {
        return state.get();
    }

    // ── monitoring ──

    public int getFailureCount() { return failureCount.get(); }
}
