package com.feign.framework.codec;

import com.feign.framework.Response;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.HashMap;

class GsonDecoderTest {

    private final GsonDecoder decoder = new GsonDecoder();

    static class User { Long id; String name; }

    @Test void testStringPassThrough() throws Exception {
        Response resp = Response.of("http://test", 200, new HashMap<>(), "\"hello\"");
        Object result = decoder.decode(resp, String.class);
        assertEquals("\"hello\"", result);
    }

    @Test void testResponsePassThrough() throws Exception {
        Response resp = Response.of("http://test", 200, new HashMap<>(), "{}");
        Object result = decoder.decode(resp, Response.class);
        assertSame(resp, result);
    }

    @Test void testByteArray() throws Exception {
        Response resp = Response.of("http://test", 200, new HashMap<>(), "hello");
        Object result = decoder.decode(resp, byte[].class);
        assertArrayEquals("hello".getBytes(), (byte[]) result);
    }

    @Test void testJsonToObject() throws Exception {
        Response resp = Response.of("http://test", 200, new HashMap<>(),
            "{\"id\":1,\"name\":\"张三\"}");
        User user = (User) decoder.decode(resp, User.class);
        assertEquals(1L, user.id);
        assertEquals("张三", user.name);
    }

    @Test void testNullBody() throws Exception {
        Response resp = Response.of("http://test", 200, new HashMap<>(), null);
        assertNull(decoder.decode(resp, String.class));
    }
}
