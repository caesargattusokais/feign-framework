package com.feign.framework.loadbalancer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link LoadBalancerType} enum.
 *
 * @author Feign Framework Team
 * @version 1.0.0-SNAPSHOT
 */
class LoadBalancerTypeTest {

    @Test
    void testGetAllValues() {
        LoadBalancerType[] values = LoadBalancerType.values();
        assertEquals(3, values.length);
    }

    @Test
    void testRoundRobinValue() {
        assertEquals("ROUND_ROBIN", LoadBalancerType.ROUND_ROBIN.name());
    }

    @Test
    void testRandomValue() {
        assertEquals("RANDOM", LoadBalancerType.RANDOM.name());
    }

    @Test
    void testLeastConnectionsValue() {
        assertEquals("LEAST_CONNECTIONS", LoadBalancerType.LEAST_CONNECTIONS.name());
    }

    @Test
    void testAllValuesAreUnique() {
        assertEquals(3, LoadBalancerType.values().length);
    }
}
