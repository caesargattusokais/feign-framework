package com.feign.framework.client;

import com.feign.framework.config.FeignConfig;
import com.feign.framework.loadbalancer.RoundRobinLoadBalancer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FeignClientFactoryTest {

    @Test
    void testCreateClient() {
        FeignClientFactory factory = new FeignClientFactory();
        HttpClient client = factory.createClient("test-service", new FeignConfig());

        assertNotNull(client);
    }

    @Test
    void testCreateClientWithLoadBalancer() {
        FeignClientFactory factory = new FeignClientFactory();
        factory.registerLoadBalancer("test-service", new RoundRobinLoadBalancer());

        HttpClient client = factory.createClient("test-service", new FeignConfig());

        assertNotNull(client);
    }
}
