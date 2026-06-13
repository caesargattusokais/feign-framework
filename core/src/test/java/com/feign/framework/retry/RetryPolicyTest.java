package com.feign.framework.retry;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RetryPolicy} interface.
 *
 * @author Feign Framework Team
 * @version 1.0.0-SNAPSHOT
 */
class RetryPolicyTest {

    @Test
    void testRetryPolicyCanRetryReturnsFalseForNullException() {
        RetryPolicy policy = new TestRetryPolicy(true, 3, 1000, null, true);
        assertFalse(policy.canRetry(null, 0));
    }

    @Test
    void testRetryPolicyCanRetryReturnsFalseForExhaustedRetries() {
        RetryPolicy policy = new TestRetryPolicy(true, 3, 1000, null, true);
        assertFalse(policy.canRetry(new RuntimeException("Test"), 3));
    }

    @Test
    void testRetryPolicyCanRetryReturnsTrueForValidException() {
        RetryPolicy policy = new TestRetryPolicy(true, 3, 1000, null, true);
        assertTrue(policy.canRetry(new RuntimeException("Test"), 0));
    }

    @Test
    void testRetryPolicyCanRetryReturnsFalseForNonRetryableException() {
        RetryPolicy policy = new TestRetryPolicy(true, 3, 1000, null, true);
        assertFalse(policy.canRetry(new IllegalArgumentException("Invalid"), 0));
    }

    @Test
    void testGetMaxRetriesReturnsCorrectValue() {
        RetryPolicy policy = new TestRetryPolicy(true, 5, 1000, null, true);
        assertEquals(5, policy.getMaxRetries());
    }

    @Test
    void testGetRetryIntervalReturnsCorrectValue() {
        RetryPolicy policy = new TestRetryPolicy(true, 3, 2000, null, true);
        assertEquals(2000, policy.getRetryInterval());
    }

    @Test
    void testGetLoadBalancerTypeReturnsCorrectValue() {
        RetryPolicy policy = new TestRetryPolicy(true, 3, 1000, "ROUND_ROBIN", true);
        assertEquals("ROUND_ROBIN", policy.getLoadBalancerType());
    }

    @Test
    void testGetLoadBalancerTypeReturnsNullForGlobalPolicy() {
        RetryPolicy policy = new TestRetryPolicy(true, 3, 1000, null, true);
        assertNull(policy.getLoadBalancerType());
    }

    @Test
    void testIsEnabledReturnsTrueForEnabledPolicy() {
        RetryPolicy policy = new TestRetryPolicy(true, 3, 1000, null, true);
        assertTrue(policy.isEnabled());
    }

    @Test
    void testIsEnabledReturnsFalseForDisabledPolicy() {
        RetryPolicy policy = new TestRetryPolicy(true, 3, 1000, null, false);
        assertFalse(policy.isEnabled());
    }

    // Test implementation for RetryPolicy
    private static class TestRetryPolicy implements RetryPolicy {
        private final boolean enabled;
        private final int maxRetries;
        private final long retryInterval;
        private final String loadBalancerType;

        TestRetryPolicy(boolean enabled, int maxRetries, long retryInterval, String loadBalancerType, boolean isEnabled) {
            this.enabled = enabled;
            this.maxRetries = maxRetries;
            this.retryInterval = retryInterval;
            this.loadBalancerType = loadBalancerType;
            this.isEnabled = isEnabled;
        }

        @Override
        public boolean canRetry(Exception e, int retryCount) {
            if (e == null) {
                return false;
            }
            if (retryCount >= maxRetries) {
                return false;
            }
            return e instanceof RuntimeException || e instanceof Exception;
        }

        @Override
        public int getMaxRetries() {
            return maxRetries;
        }

        @Override
        public long getRetryInterval() {
            return retryInterval;
        }

        @Override
        public String getLoadBalancerType() {
            return loadBalancerType;
        }

        @Override
        public boolean isEnabled() {
            return isEnabled;
        }
    }
}
