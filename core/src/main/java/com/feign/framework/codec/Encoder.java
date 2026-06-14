package com.feign.framework.codec;

import java.lang.reflect.Type;

/**
 * Encoder that converts a Java object into an HTTP request body.
 *
 * <p>Paired with {@link Decoder}. Together they handle request/response
 * serialization automatically — users pass typed objects, not raw strings.
 */
public interface Encoder {

    /** Encode an object into bytes for the request body. */
    byte[] encode(Object object, Type type) throws Exception;
}
