package com.feign.framework.retry;

/**
 * Abstraction for retry policies.
 *
 * @author Feign Framework Team
 * @version 1.0.0-SNAPSHOT
 */
public interface RetryPolicy {

    /**
     * Determines if a retry should be attempted for a given exception.
     *
     * @param e the exception that occurred
     * @param retryCount the current retry count (0 for the first attempt)
     * @return true if the request should be retried, false otherwise
     */
    boolean canRetry(Exception e, int retryCount);

    /**
     * Gets the maximum number of retry attempts.
     *
     * @return the maximum retry count (0 means no retries)
     */
    int getMaxRetries();

    /**
     * Gets the base interval between retry attempts in milliseconds.
     *
     * @return the base interval in milliseconds
     */
    long getRetryInterval();

    /**
     * Gets the specific load balancer type this retry policy applies to.
     *
     * @return the load balancer type, or null if it applies to all types
     */
    String getLoadBalancerType();

    /**
     * Checks if this retry policy is enabled.
     *
     * @return true if retries are enabled, false otherwise
     */
    boolean isEnabled();
}
