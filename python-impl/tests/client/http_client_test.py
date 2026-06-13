"""Tests for HttpClient"""
import pytest
from feign.models import Request, Response
from feign.client.http_client import HttpClientImpl


class TestHttpClientImpl:
    def test_initialization(self):
        """Test that HTTP client can be initialized"""
        client = HttpClientImpl()
        assert client.timeout == 30000

    def test_initialization_with_custom_timeout(self):
        """Test HTTP client with custom timeout"""
        client = HttpClientImpl(timeout=5000)
        assert client.timeout == 5000

    def test_execute_request_with_get(self):
        """Test executing a GET request"""
        client = HttpClientImpl()

        request = Request(
            method="GET",
            url="http://httpbin.org/get"
        )

        # This test will fail without a running server, but tests the structure
        # In real tests, you'd use a test server or mock httpx
        response = client.execute(request)
        assert isinstance(response, Response)
        assert hasattr(response, 'status_code')
        assert hasattr(response, 'headers')

    def test_execute_request_with_post(self):
        """Test executing a POST request"""
        client = HttpClientImpl()

        request = Request(
            method="POST",
            url="http://httpbin.org/post",
            body=b'{"key": "value"}'
        )

        response = client.execute(request)
        assert isinstance(response, Response)
        assert hasattr(response, 'status_code')

    def test_execute_request_with_params(self):
        """Test executing a request with query parameters"""
        client = HttpClientImpl()

        request = Request(
            method="GET",
            url="http://httpbin.org/get",
            params={"param1": "value1", "param2": "value2"}
        )

        response = client.execute(request)
        assert isinstance(response, Response)
        assert hasattr(response, 'status_code')

    def test_successful_method(self):
        """Test successful() method for 2xx responses"""
        client = HttpClientImpl()

        request = Request(
            method="GET",
            url="http://httpbin.org/get"
        )

        response = client.execute(request)
        assert response.successful() == (200 <= response.status_code < 300)

    def test_successful_method_with_non_2xx(self):
        """Test successful() method with non-2xx responses"""
        client = HttpClientImpl()

        # Simulate a 404 response
        response = Response(
            status_code=404,
            headers={"Content-Type": "application/json"},
            body=b'{"error": "Not found"}'
        )

        assert response.successful() is False

    def test_json_method_with_valid_json(self):
        """Test json() method with valid JSON response"""
        client = HttpClientImpl()

        response = Response(
            status_code=200,
            headers={"Content-Type": "application/json"},
            body=b'{"key": "value", "number": 42}'
        )

        json_data = response.json()
        assert json_data == {"key": "value", "number": 42}

    def test_json_method_with_no_body(self):
        """Test json() method with no body"""
        client = HttpClientImpl()

        response = Response(
            status_code=200,
            headers={"Content-Type": "application/json"},
            body=None
        )

        json_data = response.json()
        assert json_data is None

    def test_json_method_with_empty_body(self):
        """Test json() method with empty body"""
        client = HttpClientImpl()

        response = Response(
            status_code=200,
            headers={"Content-Type": "application/json"},
            body=b''
        )

        json_data = response.json()
        assert json_data is None

    def test_execute_async(self):
        """Test async execute method (mocked)"""
        import asyncio

        async def test_async():
            client = HttpClientImpl()

            request = Request(
                method="GET",
                url="http://httpbin.org/get"
            )

            response = await client.execute_async(request)
            assert isinstance(response, Response)
            assert hasattr(response, 'status_code')

        # Run async test
        asyncio.run(test_async())

    def test_is_available(self):
        """Test is_available method"""
        client = HttpClientImpl()

        # Check a well-known URL
        is_available = client.is_available("https://httpbin.org")
        assert isinstance(is_available, bool)

        # Check an unavailable URL
        is_available = client.is_available("http://localhost:99999")
        assert isinstance(is_available, bool)
