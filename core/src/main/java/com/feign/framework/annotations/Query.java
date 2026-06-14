package com.feign.framework.annotations;

import java.lang.annotation.*;

/**
 * Bind a method parameter to a query parameter.
 *
 * <pre>{@code
 * @FeignMethod(method = HttpMethod.GET, path = {"users"})
 * List<User> listUsers(@Query("page") int page, @Query("size") int size);
 * // → GET /users?page=1&size=10
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Query {
    String value();
}
