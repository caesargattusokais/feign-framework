package com.feign.framework.annotations;

import java.lang.annotation.*;

/**
 * Explicitly mark a parameter as the request body.
 * Without @Body, the first non-@Path/ non-@Query object parameter is used.
 *
 * <pre>{@code
 * @FeignMethod(method = HttpMethod.POST, path = {"users"})
 * User createUser(@Body User user);
 *
 * // raw body (skip Encoder)
 * @FeignMethod(method = HttpMethod.POST, path = {"users"})
 * User createUser(@Body(raw = true) String rawJson);
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Body {
    /** If true, send the parameter value directly without Encoder serialization. */
    boolean raw() default false;
}
