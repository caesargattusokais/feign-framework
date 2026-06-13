"""Tests for RoundRobinLoadBalancer"""
import pytest
from feign.loadbalancer.round_robin import RoundRobinLoadBalancer


class TestRoundRobinLoadBalancer:
    def test_add_server(self):
        """Test adding servers to the load balancer"""
        balancer = RoundRobinLoadBalancer()
        balancer.add_server("http://server1:8080")
        balancer.add_server("http://server2:8080")
        balancer.add_server("http://server3:8080")

        server = balancer.select("test-service")
        assert server is not None
        assert any(s in server for s in ["server1", "server2", "server3"])

    def test_select_with_empty_servers(self):
        """Test that selecting from empty load balancer raises exception"""
        balancer = RoundRobinLoadBalancer()
        with pytest.raises(Exception) as exc_info:
            balancer.select("test-service")
        assert "No servers available" in str(exc_info.value)

    def test_remove_server(self):
        """Test removing a server from the load balancer"""
        balancer = RoundRobinLoadBalancer()
        balancer.add_server("http://server1:8080")
        balancer.add_server("http://server2:8080")

        server1 = balancer.select("test-service")
        assert server1 is not None

        balancer.remove_server("http://server1:8080")

        server2 = balancer.select("test-service")
        assert server2 is not None
        assert server2 != server1

    def test_reset(self):
        """Test resetting the load balancer"""
        balancer = RoundRobinLoadBalancer()
        balancer.add_server("http://server1:8080")
        balancer.add_server("http://server2:8080")

        balancer.select("test-service")
        balancer.select("test-service")

        balancer.reset()

        with pytest.raises(Exception) as exc_info:
            balancer.select("test-service")
        assert "No servers available" in str(exc_info.value)

    def test_round_robin_distribution(self):
        """Test that round-robin distributes requests evenly"""
        balancer = RoundRobinLoadBalancer()
        balancer.add_server("http://server1:8080")
        balancer.add_server("http://server2:8080")

        servers = {balancer.select("test-service") for _ in range(4)}
        # Should have returned server1 twice and server2 twice
        assert len(servers) == 2

    def test_get_servers(self):
        """Test getting the list of registered servers"""
        balancer = RoundRobinLoadBalancer()
        balancer.add_server("http://server1:8080")
        balancer.add_server("http://server2:8080")

        servers = balancer.get_servers()
        assert len(servers) == 2
        assert "http://server1:8080" in servers
        assert "http://server2:8080" in servers
