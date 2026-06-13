package com.feign.processor;

import com.feign.framework.annotations.FeignClient;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FeignClientMetadata class.
 */
class FeignClientMetadataTest {

    @Test
    void testCreateMetadataFromAnnotation() {
        FeignClientMetadata metadata = new FeignClientMetadata(
            "user-service",
            "http://localhost:8080",
            "ROUND_ROBIN",
            new String[]{"path"}
        );

        assertEquals("user-service", metadata.getServiceName());
        assertEquals("http://localhost:8080", metadata.getUrl());
        assertEquals("ROUND_ROBIN", metadata.getLoadBalancer());
        assertEquals(0, metadata.getPath().length);
    }

    @Test
    void testMetadataWithEmptyPath() {
        FeignClientMetadata metadata = new FeignClientMetadata(
            "test-service",
            "http://test:8080",
            "default",
            new String[]{""}
        );

        assertEquals("test-service", metadata.getServiceName());
        assertEquals("http://test:8080", metadata.getUrl());
        assertEquals("default", metadata.getLoadBalancer());
        assertEquals(1, metadata.getPath().length);
        assertEquals("", metadata.getPath()[0]);
    }
}
