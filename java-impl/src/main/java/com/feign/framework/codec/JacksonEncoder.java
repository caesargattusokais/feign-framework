package com.feign.framework.codec;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Type;

/**
 * Encoder using Jackson ObjectMapper.
 */
public class JacksonEncoder implements Encoder {
    private final ObjectMapper mapper;

    public JacksonEncoder()                   { this.mapper = new ObjectMapper(); }
    public JacksonEncoder(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    public byte[] encode(Object object, Type type) throws Exception {
        if (object == null)        return new byte[0];
        if (object instanceof byte[] b) return b;
        if (object instanceof String s)  return s.getBytes();

        JavaType javaType = mapper.getTypeFactory().constructType(type);
        return mapper.writerFor(javaType).writeValueAsBytes(object);
    }
}
