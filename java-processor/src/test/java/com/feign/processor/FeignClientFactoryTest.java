package com.feign.processor;

import com.feign.framework.FeignException;
import com.feign.framework.annotations.FeignClient;
import com.feign.framework.annotations.FeignMethod;
import com.feign.framework.http.HttpMethod;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FeignClientFactory class.
 */
class FeignClientFactoryTest {

    @FeignClient(name = "user-service", url = "http://localhost:8080")
    interface UserService {
        @FeignMethod(method = HttpMethod.GET, path = {"users", "{id}"})
        String getUser(Long id);

        @FeignMethod(method = HttpMethod.POST, path = {"users"})
        String createUser(String name);
    }

    @Test
    void testCreateClient() {
        UserService userService = FeignClientFactory.create(UserService.class);

        assertNotNull(userService);
        assertTrue(userService instanceof Proxy);
    }

    @Test
    void testCreateClientWithCustomUrl() {
        UserService userService = FeignClientFactory.create(
            "http://custom-url:8080",
            UserService.class
        );

        assertNotNull(userService);
        assertTrue(userService instanceof Proxy);
    }

    @Test
    void testCreateClientWithoutAnnotation() {
        interface InvalidInterface {
            String method();
        }

        assertThrows(FeignException.class, () -> {
            FeignClientFactory.create(InvalidInterface.class);
        });
    }
}
