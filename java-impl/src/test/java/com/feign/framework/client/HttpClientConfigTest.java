package com.feign.framework.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HttpClientConfigTest {

    @Test
    void testDefaultValues() {
        HttpClientConfig config = new HttpClientConfig();

        assertEquals(5000, config.getConnectTimeout());
        assertEquals(5000, config.getReadTimeout());
        assertFalse(config.isEnableLogging());
    }

    @Test
    void testCustomValues() {
        HttpClientConfig config = new HttpClientConfig();
        config.setConnectTimeout(3000);
        config.setReadTimeout(7000);
        config.setEnableLogging(true);

        assertEquals(3000, config.getConnectTimeout());
        assertEquals(7000, config.getReadTimeout());
        assertTrue(config.isEnableLogging());
    }
}
