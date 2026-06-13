package com.feign.processor;

import com.feign.framework.FeignException;
import com.feign.framework.annotations.FeignMethod;
import com.feign.framework.http.HttpMethod;
import com.feign.framework.http.Request;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FeignClientProxy class.
 */
class FeignClientProxyTest {

    interface UserService {
        @FeignMethod(method = HttpMethod.GET, path = {"users", "{id}"})
        String getUser(Long id);

        @FeignMethod(method = HttpMethod.POST, path = {"users"})
        String createUser(String name);
    }



    @Test
    void testInvokeMethodWithCorrectAnnotation() throws Throwable {
        FeignClientMetadata metadata = new FeignClientMetadata("test-service", "http://localhost:8080", "ROUND_ROBIN", new String[]{});
        FeignClientProxy proxy = new FeignClientProxy(metadata);

        Method testMethod = UserService.class.getMethod("getUser", Long.class);

        Object result = proxy.invoke(
            null,
            testMethod,
            new Object[]{1L}
        );

        assertNotNull(result);
        assertTrue(result instanceof Request);
        Request request = (Request) result;
        assertEquals(HttpMethod.GET, request.getMethod());
        assertTrue(request.getUrl().contains("users"));
        assertTrue(request.getUrl().contains("1"));
    }

    @Test
    void testInvokeMethodWithoutAnnotation() {
        FeignClientMetadata metadata = new FeignClientMetadata("test-service", "http://localhost:8080", "ROUND_ROBIN", new String[]{});
        FeignClientProxy proxy = new FeignClientProxy(metadata);

        // Create a method reference that's not annotated with @FeignMethod
        Method method;
        try {
            method = Object.class.getMethod("toString");
        } catch (NoSuchMethodException e) {
            return;
        }

        assertThrows(FeignException.class, () -> {
            proxy.invoke(new Object(), method, new Object[]{});
        });
    }
}
