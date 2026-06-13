package com.feign.framework.annotations;

import com.feign.framework.http.HttpMethod;
import java.lang.annotation.*;

/**
 * Annotation to mark a method as a Feign client method.
 * Used by the annotation processor to build HTTP requests.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface FeignMethod {
    HttpMethod method() default HttpMethod.GET;

    String[] path() default {};

    String[] headers() default {};

    String name() default "";
}
