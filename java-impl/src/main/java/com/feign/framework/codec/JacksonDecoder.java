package com.feign.framework.codec;

import com.feign.framework.Response;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Type;

/**
 * Decoder using Jackson ObjectMapper.
 * Auto-detected in Spring when jackson-databind is on classpath.
 */
public class JacksonDecoder implements Decoder {
    private final ObjectMapper mapper;

    public JacksonDecoder()                   { this.mapper = new ObjectMapper(); }
    public JacksonDecoder(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    public Object decode(Response response, Type type) throws Exception {
        if (type == String.class)    return response.getBodyAsString();
        if (type == Response.class)  return response;
        if (type == byte[].class)    return response.body();

        String body = response.getBodyAsString();
        if (body == null || body.isEmpty()) return null;

        JavaType javaType = mapper.getTypeFactory().constructType(type);
        return mapper.readValue(body, javaType);
    }
}
