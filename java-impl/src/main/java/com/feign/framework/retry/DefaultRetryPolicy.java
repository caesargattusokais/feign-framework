package com.feign.framework.retry;

import java.io.IOException;

/**
 * Default retry policy implementation.
 * Retries on RuntimeException, IOException, and FeignException.
 */
public class DefaultRetryPolicy implements RetryPolicy {
    private int maxRetries = 3;
    private long retryInterval = 1000L;
    private String loadBalancerType = "ROUND_ROBIN";
    private boolean enabled = true;

    @Override
    public boolean canRetry(Exception e, int retryCount) {
        if (!enabled) {
            return false;
        }

        if (retryCount >= maxRetries) {
            return false;
        }

        // Can retry on runtime exceptions
        if (e instanceof RuntimeException) {
            return true;
        }

        // Can retry on IOException
        if (e instanceof IOException) {
            return true;
        }

        // Can retry on FeignException
        if (e instanceof com.feign.framework.FeignException) {
            return true;
        }

        return false;
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
        return enabled;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public void setRetryInterval(long retryInterval) {
        this.retryInterval = retryInterval;
    }

    public void setLoadBalancerType(String loadBalancerType) {
        this.loadBalancerType = loadBalancerType;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
