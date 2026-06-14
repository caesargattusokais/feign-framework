package com.feign.framework.codec;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class GsonEncoderTest {

    private final GsonEncoder encoder = new GsonEncoder();

    static class User { Long id; String name; User(){} User(Long id, String n){this.id=id;this.name=n;} }

    @Test void testStringPassThrough() throws Exception {
        byte[] result = encoder.encode("hello", String.class);
        assertEquals("hello", new String(result));
    }

    @Test void testByteArrayPassThrough() throws Exception {
        byte[] input = {1,2,3};
        assertSame(input, encoder.encode(input, byte[].class));
    }

    @Test void testNull() throws Exception {
        assertEquals(0, encoder.encode(null, String.class).length);
    }

    @Test void testObjectToJson() throws Exception {
        User u = new User(1L, "张三");
        byte[] result = encoder.encode(u, User.class);
        String json = new String(result);
        assertTrue(json.contains("\"id\":1"));
        assertTrue(json.contains("\"name\":\"张三\""));
    }
}
