package com.feign.framework.annotations;

import com.feign.framework.loadbalancer.LoadBalancerType;
import java.lang.annotation.*;

/**
 * Annotation to mark an interface as a Feign client.
 *
 * <p>Example:
 * <pre>{@code
 * @FeignClient(name = "user-service", url = "http://localhost:8080",
 *              loadBalancer = LoadBalancerType.ROUND_ROBIN,
 *              connectTimeout = 5000, readTimeout = 10000,
 *              maxRetries = 3, retryInterval = 1000)
 * public interface UserService { ... }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface FeignClient {

    /** Service name (used for service discovery). */
    String name() default "";

    /** Base URL of the service. */
    String url() default "";

    /** Base path prefix for all methods. */
    String[] path() default {};

    /** Load balancing strategy. */
    LoadBalancerType loadBalancer() default LoadBalancerType.ROUND_ROBIN;

    /** Connection timeout in milliseconds. */
    int connectTimeout() default 5000;

    /** Read timeout in milliseconds. */
    int readTimeout() default 10000;

    /** Maximum number of retry attempts. 0 means no retry. */
    int maxRetries() default 3;

    /** Delay between retries in milliseconds. */
    long retryInterval() default 1000;

    /** Fallback class — must implement this interface. Called when all retries fail. */
    Class<?> fallback() default Void.class;
}
