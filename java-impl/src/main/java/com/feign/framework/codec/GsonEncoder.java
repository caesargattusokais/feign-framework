package com.feign.framework.codec;

import com.google.gson.Gson;
import java.lang.reflect.Type;

/**
 * Encoder that uses Gson to serialize request bodies to JSON.
 */
public class GsonEncoder implements Encoder {
    private final Gson gson;

    public GsonEncoder()                 { this.gson = new Gson(); }
    public GsonEncoder(Gson gson)        { this.gson = gson; }

    @Override
    public byte[] encode(Object object, Type type) throws Exception {
        if (object == null) return new byte[0];
        if (object instanceof String s)  return s.getBytes();
        if (object instanceof byte[] b)  return b;
        return gson.toJson(object, type).getBytes();
    }
}
