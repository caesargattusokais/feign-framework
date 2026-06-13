package com.feign.framework.codec;

import com.feign.framework.Response;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;

/**
 * Decoder that uses Gson to deserialize JSON response bodies.
 */
public class GsonDecoder implements Decoder {
    private final Gson gson;

    public GsonDecoder() {
        this.gson = new Gson();
    }

    public GsonDecoder(Gson gson) {
        this.gson = gson;
    }

    @Override
    public Object decode(Response response, Type type) throws Exception {
        String body = response.getBodyAsString();
        if (body == null || body.isEmpty()) {
            return null;
        }
        if (type == String.class) {
            return body;
        }
        if (type == Response.class) {
            return response;
        }
        if (type == byte[].class) {
            return response.getBody();
        }
        // Use Gson to deserialize
        return gson.fromJson(body, type);
    }
}
