package com.feign.framework.client;

/**
 * HTTP client configuration class.
 * Defines timeout and logging settings for the HTTP client.
 */
public class HttpClientConfig {
    private int connectTimeout = 5000;
    private int readTimeout = 5000;
    private boolean enableLogging = false;

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public boolean isEnableLogging() {
        return enableLogging;
    }

    public void setEnableLogging(boolean enableLogging) {
        this.enableLogging = enableLogging;
    }
}
