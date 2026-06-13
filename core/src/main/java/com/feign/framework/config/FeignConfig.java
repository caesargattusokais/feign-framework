package com.feign.framework.config;

import com.feign.framework.client.HttpClient;
import com.feign.framework.http.HttpMethod;
import com.feign.framework.loadbalancer.LoadBalancer;
import com.feign.framework.retry.RetryPolicy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration class for Feign framework.
 * Provides a default configuration that can be customized.
 *
 * @author Feign Framework Team
 * @version 1.0.0-SNAPSHOT
 */
public class FeignConfig {

    private HttpClient httpClient;
    private LoadBalancer loadBalancer;
    private RetryPolicy retryPolicy;

    private String baseUrl;
    private Map<String, String> defaultHeaders = new HashMap<>();
    private Map<String, String> defaultQueryParams = new HashMap<>();
    private List<HttpMethod> allowedMethods = new ArrayList<>();

    // Retry configuration
    private boolean retryEnabled = true;
    private int maxRetries = 3;
    private long retryInterval = 1000;
    private String retryLoadBalancerType = "ROUND_ROBIN";

    // Timeout configuration
    private int connectTimeout = 5000;
    private int readTimeout = 10000;

    // Client configuration
    private boolean followRedirects = true;
    private boolean compressRequests = false;
    private boolean compressResponses = false;

    /**
     * Creates a default Feign configuration.
     */
    public FeignConfig() {
        this.allowedMethods.add(HttpMethod.GET);
        this.allowedMethods.add(HttpMethod.POST);
        this.allowedMethods.add(HttpMethod.PUT);
        this.allowedMethods.add(HttpMethod.DELETE);
    }

    /**
     * Creates a new FeignConfig with the specified base URL.
     *
     * @param baseUrl the base URL for all requests
     */
    public FeignConfig(String baseUrl) {
        this();
        this.baseUrl = baseUrl;
    }

    /**
     * Sets the HTTP client to use for requests.
     *
     * @param httpClient the HTTP client
     * @return this configuration instance
     */
    public FeignConfig withHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
        return this;
    }

    /**
     * Sets the load balancer to use for request routing.
     *
     * @param loadBalancer the load balancer
     * @return this configuration instance
     */
    public FeignConfig withLoadBalancer(LoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
        return this;
    }

    /**
     * Sets the retry policy to use for failed requests.
     *
     * @param retryPolicy the retry policy
     * @return this configuration instance
     */
    public FeignConfig withRetryPolicy(RetryPolicy retryPolicy) {
        this.retryPolicy = retryPolicy;
        return this;
    }

    /**
     * Sets the base URL for all requests.
     *
     * @param baseUrl the base URL
     * @return this configuration instance
     */
    public FeignConfig withBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    /**
     * Adds a default header to all requests.
     *
     * @param name the header name
     * @param value the header value
     * @return this configuration instance
     */
    public FeignConfig withDefaultHeader(String name, String value) {
        this.defaultHeaders.put(name, value);
        return this;
    }

    /**
     * Adds a default query parameter to all requests.
     *
     * @param name the parameter name
     * @param value the parameter value
     * @return this configuration instance
     */
    public FeignConfig withDefaultQueryParam(String name, String value) {
        this.defaultQueryParams.put(name, value);
        return this;
    }

    /**
     * Adds an allowed HTTP method.
     *
     * @param method the HTTP method
     * @return this configuration instance
     */
    public FeignConfig withAllowedMethod(HttpMethod method) {
        this.allowedMethods.add(method);
        return this;
    }

    /**
     * Sets whether retry is enabled.
     *
     * @param enabled true if retry is enabled
     * @return this configuration instance
     */
    public FeignConfig withRetryEnabled(boolean enabled) {
        this.retryEnabled = enabled;
        return this;
    }

    /**
     * Sets the maximum number of retries.
     *
     * @param maxRetries the maximum retry count
     * @return this configuration instance
     */
    public FeignConfig withMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
        return this;
    }

    /**
     * Sets the retry interval in milliseconds.
     *
     * @param retryInterval the retry interval
     * @return this configuration instance
     */
    public FeignConfig withRetryInterval(long retryInterval) {
        this.retryInterval = retryInterval;
        return this;
    }

    /**
     * Sets the load balancer type for retry policy.
     *
     * @param retryLoadBalancerType the load balancer type
     * @return this configuration instance
     */
    public FeignConfig withRetryLoadBalancerType(String retryLoadBalancerType) {
        this.retryLoadBalancerType = retryLoadBalancerType;
        return this;
    }

    /**
     * Sets the connection timeout in milliseconds.
     *
     * @param connectTimeout the connection timeout
     * @return this configuration instance
     */
    public FeignConfig withConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    /**
     * Sets the read timeout in milliseconds.
     *
     * @param readTimeout the read timeout
     * @return this configuration instance
     */
    public FeignConfig withReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
        return this;
    }

    /**
     * Sets whether to follow redirects.
     *
     * @param followRedirects true to follow redirects
     * @return this configuration instance
     */
    public FeignConfig withFollowRedirects(boolean followRedirects) {
        this.followRedirects = followRedirects;
        return this;
    }

    /**
     * Sets whether to compress requests.
     *
     * @param compressRequests true to compress requests
     * @return this configuration instance
     */
    public FeignConfig withCompressRequests(boolean compressRequests) {
        this.compressRequests = compressRequests;
        return this;
    }

    /**
     * Sets whether to compress responses.
     *
     * @param compressResponses true to compress responses
     * @return this configuration instance
     */
    public FeignConfig withCompressResponses(boolean compressResponses) {
        this.compressResponses = compressResponses;
        return this;
    }

    // Getters

    public HttpClient getHttpClient() {
        return httpClient;
    }

    public LoadBalancer getLoadBalancer() {
        return loadBalancer;
    }

    public RetryPolicy getRetryPolicy() {
        return retryPolicy;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public Map<String, String> getDefaultHeaders() {
        return new HashMap<>(defaultHeaders);
    }

    public Map<String, String> getDefaultQueryParams() {
        return new HashMap<>(defaultQueryParams);
    }

    public List<HttpMethod> getAllowedMethods() {
        return new ArrayList<>(allowedMethods);
    }

    public boolean isRetryEnabled() {
        return retryEnabled;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public long getRetryInterval() {
        return retryInterval;
    }

    public String getRetryLoadBalancerType() {
        return retryLoadBalancerType;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public boolean isFollowRedirects() {
        return followRedirects;
    }

    public boolean isCompressRequests() {
        return compressRequests;
    }

    public boolean isCompressResponses() {
        return compressResponses;
    }
}
