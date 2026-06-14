package com.feign.framework.annotations;

import java.lang.annotation.*;

/**
 * Bind a method parameter to a dynamic request header.
 *
 * <pre>{@code
 * @FeignMethod(method = HttpMethod.GET, path = {"users"})
 * List<User> list(@Header("Authorization") String token, @Header("X-Trace-Id") String traceId);
 * // → headers: {Authorization: Bearer xxx, X-Trace-Id: abc123}
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Header {
    String value();
}
