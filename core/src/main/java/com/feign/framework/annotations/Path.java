package com.feign.framework.annotations;

import java.lang.annotation.*;

/**
 * Annotation to bind a method parameter to a URI template variable.
 * Example: @FeignMethod(path = {"users", "{id}"}) with @Path("id") Long id
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Path {
    /** The URI template variable name */
    String value();
}
