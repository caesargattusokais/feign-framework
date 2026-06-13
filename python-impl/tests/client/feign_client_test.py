"""Tests for FeignClient"""
import pytest
from unittest.mock import Mock, patch, AsyncMock
from feign import FeignClient
from feign.models import Request, FeignClientConfig


class MockUserService(FeignClient):
    """Mock user service for testing"""

    def __init__(self):
        config = FeignClientConfig(
            name="mock-user-service",
            url="http://localhost:8080"
        )
        super().__init__(config)

    def get_user(self, user_id: int) -> dict:
        return self.get_user(user_id)

    def create_user(self, user_data: dict) -> dict:
        return self.create_user(user_data)

    def update_user(self, user_id: int, user_data: dict) -> dict:
        return self.update_user(user_id, user_data)

    def delete_user(self, user_id: int) -> dict:
        return self.delete_user(user_id)


class TestFeignClient:
    def test_initialization_with_config(self):
        """Test Feign client initialization with config"""
        config = FeignClientConfig(
            name="test-service",
            url="http://localhost:8080",
            load_balancer_type="ROUND_ROBIN"
        )

        client = FeignClient(config=config)
        assert client._config.name == "test-service"
        assert client._config.url == "http://localhost:8080"
        assert client._config.load_balancer_type == "ROUND_ROBIN"

    def test_initialization_with_class_attributes(self):
        """Test Feign client initialization from class attributes"""
        class UserService(FeignClient):
            _name = "user-service"
            _url = "http://localhost:8080"
            _load_balancer_type = "ROUND_ROBIN"

        client = UserService()
        assert client._config.name == "user-service"
        assert client._config.url == "http://localhost:8080"

    def test_initialization_missing_required_attributes(self):
        """Test that initialization fails without required attributes"""
        with pytest.raises(ValueError, match="must have _name attribute"):
            class UserService(FeignClient):
                pass

        with pytest.raises(ValueError, match="must have _url attribute"):
            class UserService(FeignClient):
                _name = "user-service"

    def test_get_user_method(self):
        """Test that get_user method is created dynamically"""
        client = MockUserService()

        assert hasattr(client, 'get_user')
        assert callable(client.get_user)

    def test_create_user_method(self):
        """Test that create_user method is created dynamically"""
        client = MockUserService()

        assert hasattr(client, 'create_user')
        assert callable(client.create_user)

    def test_update_user_method(self):
        """Test that update_user method is created dynamically"""
        client = MockUserService()

        assert hasattr(client, 'update_user')
        assert callable(client.update_user)

    def test_delete_user_method(self):
        """Test that delete_user method is created dynamically"""
        client = MockUserService()

        assert hasattr(client, 'delete_user')
        assert callable(client.delete_user)

    def test_unknown_method_raises_attribute_error(self):
        """Test that accessing unknown method raises AttributeError"""
        client = MockUserService()

        with pytest.raises(AttributeError, match="has no attribute"):
            client.unknown_method()

    def test_http_method_determination(self):
        """Test HTTP method determination from method name"""
        client = MockUserService()

        assert client._determine_http_method("get_user") == "GET"
        assert client._determine_http_method("create_user") == "POST"
        assert client._determine_http_method("update_user") == "PUT"
        assert client._determine_http_method("delete_user") == "DELETE"

    def test_url_building(self):
        """Test URL construction for different endpoints"""
        client = MockUserService()

        # Test GET endpoint
        method = client.get_user
        assert method(123)._config.url == "http://localhost:8080/get_user/123"

        # Test POST endpoint
        method = client.create_user
        assert method({"name": "test"})._config.url == "http://localhost:8080/create_user"

        # Test PUT endpoint
        method = client.update_user
        assert method(456, {"name": "test"})._config.url == "http://localhost:8080/update_user/456"

        # Test DELETE endpoint
        method = client.delete_user
        assert method(789)._config.url == "http://localhost:8080/delete_user/789"

    @patch('feign.client.feign_client.HttpClientImpl')
    def test_execute_request(self, mock_http_client_class):
        """Test HTTP request execution"""
        # Setup mock
        mock_http_client = Mock()
        mock_response = Mock()
        mock_response.status_code = 200
        mock_response.headers = {"Content-Type": "application/json"}
        mock_response.body = b'{"result": "success"}'
        mock_http_client.execute.return_value = mock_response
        mock_http_client_class.return_value = mock_http_client

        # Execute request
        client = MockUserService()
        response = client.get_user(123)

        # Verify HTTP client was called
        mock_http_client.execute.assert_called_once()

    @patch('feign.client.feign_client.HttpClientImpl')
    def test_register_server(self, mock_http_client_class):
        """Test server registration with load balancer"""
        client = MockUserService()
        mock_load_balancer = Mock()
        client._load_balancer = mock_load_balancer

        # Register server
        client._register_server("http://localhost:8081")

        # Verify load balancer was called
        mock_load_balancer.add_server.assert_called_once_with("http://localhost:8081")

    @patch('feign.client.feign_client.HttpClientImpl')
    def test_get_servers(self, mock_http_client_class):
        """Test getting registered servers"""
        client = MockUserService()
        mock_load_balancer = Mock()
        mock_load_balancer.get_servers.return_value = ["http://server1:8080", "http://server2:8080"]
        client._load_balancer = mock_load_balancer

        # Get servers
        servers = client._get_servers()

        # Verify load balancer was called
        assert servers == ["http://server1:8080", "http://server2:8080"]
        mock_load_balancer.get_servers.assert_called_once()
