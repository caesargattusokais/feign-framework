package com.feign.framework.annotations;

import java.lang.annotation.*;

/**
 * Marker annotation for default map values in Feign annotations.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface DefaultMap {
    String[] value() default {};
}
