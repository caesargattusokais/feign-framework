package com.feign.framework.retry;

import com.feign.framework.FeignException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DefaultRetryPolicyTest {

    @Test
    void testDefaultValues() {
        DefaultRetryPolicy policy = new DefaultRetryPolicy();

        assertEquals(3, policy.getMaxRetries());
        assertEquals(1000L, policy.getRetryInterval());
        assertEquals("ROUND_ROBIN", policy.getLoadBalancerType());
        assertTrue(policy.isEnabled());
    }

    @Test
    void testCanRetryWithRetryCount() {
        DefaultRetryPolicy policy = new DefaultRetryPolicy();

        assertTrue(policy.canRetry(new RuntimeException("Error"), 0));
        assertTrue(policy.canRetry(new RuntimeException("Error"), 1));
        assertTrue(policy.canRetry(new RuntimeException("Error"), 2));
        assertFalse(policy.canRetry(new RuntimeException("Error"), 3));
    }

    @Test
    void testCanRetryWithException() {
        DefaultRetryPolicy policy = new DefaultRetryPolicy();

        // Can retry on RuntimeException
        assertTrue(policy.canRetry(new RuntimeException("Network error"), 0));

        // Can retry on FeignException
        assertTrue(policy.canRetry(new FeignException(500, "http://api", "Error"), 1));

        // Can retry on IOException
        assertTrue(policy.canRetry(new java.io.IOException("Connection failed"), 2));
    }

    @Test
    void testCustomRetryPolicy() {
        DefaultRetryPolicy policy = new DefaultRetryPolicy();
        policy.setMaxRetries(5);
        policy.setRetryInterval(2000L);

        assertEquals(5, policy.getMaxRetries());
        assertEquals(2000L, policy.getRetryInterval());
    }

    @Test
    void testDisabledRetry() {
        DefaultRetryPolicy policy = new DefaultRetryPolicy();
        policy.setEnabled(false);

        assertFalse(policy.isEnabled());
        assertFalse(policy.canRetry(new RuntimeException("Error"), 0));
    }
}
