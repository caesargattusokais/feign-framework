package com.feign.framework.discovery;

import java.util.List;

/**
 * Service discovery abstraction.
 *
 * <p>Implementations retrieve server addresses from a registry
 * (Nacos, Consul, Eureka, etc.) so @FeignClient doesn't need a hardcoded URL.
 *
 * <pre>{@code
 * @FeignClient(name = "user-service")  // URL auto-resolved from discovery
 * public interface UserService { ... }
 * }</pre>
 */
public interface ServiceDiscovery {

    /** Get all available instances for a service name. */
    List<String> getInstances(String serviceName);

    /** Register this service instance (optional). */
    default void register(String serviceName, String host, int port) {}

    /** Deregister on shutdown (optional). */
    default void deregister(String serviceName) {}
}
