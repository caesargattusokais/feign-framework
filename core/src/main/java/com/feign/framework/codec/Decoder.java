package com.feign.framework.codec;

import com.feign.framework.Response;
import java.lang.reflect.Type;

/**
 * Decoder that converts an HTTP {@link Response} into a typed Java object.
 *
 * <p>This is analogous to OpenFeign's {@code feign.codec.Decoder}.
 * Users provide their own implementation (Jackson, Gson, Protobuf, etc.)
 * to automatically deserialize response bodies.
 *
 * <h3>Built-in implementations</h3>
 * <ul>
 *   <li>{@code GsonDecoder} — uses Gson (provided)</li>
 *   <li>{@code StringDecoder} — returns raw body string</li>
 *   <li>{@code ResponseDecoder} — returns the raw Response object</li>
 * </ul>
 *
 * <h3>Custom decoder example</h3>
 * <pre>{@code
 * public class JacksonDecoder implements Decoder {
 *     private final ObjectMapper mapper = new ObjectMapper();
 *     public Object decode(Response response, Type type) throws Exception {
 *         return mapper.readValue(response.getBodyAsString(), ...);
 *     }
 * }
 * }</pre>
 */
public interface Decoder {

    /**
     * Decode an HTTP response into an object of the given type.
     *
     * @param response the HTTP response
     * @param type     the target type to decode into
     * @return the decoded object
     * @throws Exception if decoding fails
     */
    Object decode(Response response, Type type) throws Exception;
}
