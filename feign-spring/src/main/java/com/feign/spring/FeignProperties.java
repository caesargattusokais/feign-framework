package com.feign.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Feign client configuration properties.
 *
 * <p>Example (application.yml):
 * <pre>{@code
 * feign:
 *   client:
 *     config:
 *       default:
 *         url: http://localhost:8080
 *         connect-timeout: 5000
 *         read-timeout: 10000
 *         max-retries: 3
 *         retry-interval: 1000
 *         load-balancer: ROUND_ROBIN
 *         decoder: gson
 *         protocol: http
 *         interceptors:
 *           - loggingInterceptor
 *           - authInterceptor
 *         connection-pool:
 *           max-total: 200
 *           max-per-route: 20
 *         load-balancer-bean: myLoadBalancer
 * }</pre>
 */
@ConfigurationProperties(prefix = "feign.client")
public class FeignProperties {

    /** Per-service configuration, keyed by service name. "default" applies globally. */
    private Map<String, ClientConfig> config = new HashMap<>();

    public Map<String, ClientConfig> getConfig() { return config; }
    public void setConfig(Map<String, ClientConfig> config) { this.config = config; }

    /** Get merged config for a service (inherits from "default"). */
    public ClientConfig getMerged(String serviceName) {
        ClientConfig defaults = config.getOrDefault("default", new ClientConfig());
        ClientConfig specific = config.getOrDefault(serviceName, new ClientConfig());
        return defaults.merge(specific);
    }

    public static class ClientConfig {
        private String url;
        private Integer connectTimeout;
        private Integer readTimeout;
        private Integer maxRetries;
        private Long retryInterval;
        private String loadBalancer;       // ROUND_ROBIN | RANDOM | LEAST_CONNECTIONS
        private String loadBalancerBean;   // Spring bean name for custom LoadBalancer
        private String decoder;            // "gson" | "jackson" | bean name
        private String protocol;           // "http" | "grpc" | "ws"
        private List<String> interceptors; // Spring bean names
        private ConnectionPool connectionPool;
        private String protocolHandlerBean; // Custom ProtocolHandler bean name

        // --- merge: specific overrides default ---
        public ClientConfig merge(ClientConfig specific) {
            ClientConfig merged = new ClientConfig();
            merged.url = specific.url != null ? specific.url : this.url;
            merged.connectTimeout = specific.connectTimeout != null ? specific.connectTimeout : this.connectTimeout;
            merged.readTimeout = specific.readTimeout != null ? specific.readTimeout : this.readTimeout;
            merged.maxRetries = specific.maxRetries != null ? specific.maxRetries : this.maxRetries;
            merged.retryInterval = specific.retryInterval != null ? specific.retryInterval : this.retryInterval;
            merged.loadBalancer = specific.loadBalancer != null ? specific.loadBalancer : this.loadBalancer;
            merged.loadBalancerBean = specific.loadBalancerBean != null ? specific.loadBalancerBean : this.loadBalancerBean;
            merged.decoder = specific.decoder != null ? specific.decoder : this.decoder;
            merged.protocol = specific.protocol != null ? specific.protocol : this.protocol;
            merged.interceptors = specific.interceptors != null ? specific.interceptors : this.interceptors;
            merged.connectionPool = specific.connectionPool != null ? specific.connectionPool : this.connectionPool;
            merged.protocolHandlerBean = specific.protocolHandlerBean != null ? specific.protocolHandlerBean : this.protocolHandlerBean;
            return merged;
        }

        public static class ConnectionPool {
            private Integer maxTotal;
            private Integer maxPerRoute;

            public Integer getMaxTotal() { return maxTotal; }
            public void setMaxTotal(Integer maxTotal) { this.maxTotal = maxTotal; }
            public Integer getMaxPerRoute() { return maxPerRoute; }
            public void setMaxPerRoute(Integer maxPerRoute) { this.maxPerRoute = maxPerRoute; }
        }

        // getters / setters
        public String getUrl() { return url; } public void setUrl(String url) { this.url = url; }
        public Integer getConnectTimeout() { return connectTimeout; } public void setConnectTimeout(Integer v) { this.connectTimeout = v; }
        public Integer getReadTimeout() { return readTimeout; } public void setReadTimeout(Integer v) { this.readTimeout = v; }
        public Integer getMaxRetries() { return maxRetries; } public void setMaxRetries(Integer v) { this.maxRetries = v; }
        public Long getRetryInterval() { return retryInterval; } public void setRetryInterval(Long v) { this.retryInterval = v; }
        public String getLoadBalancer() { return loadBalancer; } public void setLoadBalancer(String v) { this.loadBalancer = v; }
        public String getLoadBalancerBean() { return loadBalancerBean; } public void setLoadBalancerBean(String v) { this.loadBalancerBean = v; }
        public String getDecoder() { return decoder; } public void setDecoder(String v) { this.decoder = v; }
        public String getProtocol() { return protocol; } public void setProtocol(String v) { this.protocol = v; }
        public List<String> getInterceptors() { return interceptors; } public void setInterceptors(List<String> v) { this.interceptors = v; }
        public ConnectionPool getConnectionPool() { return connectionPool; } public void setConnectionPool(ConnectionPool v) { this.connectionPool = v; }
        public String getProtocolHandlerBean() { return protocolHandlerBean; } public void setProtocolHandlerBean(String v) { this.protocolHandlerBean = v; }
    }
}
