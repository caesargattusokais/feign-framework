"""HTTP client interface and implementation"""
from abc import ABC, abstractmethod
from typing import Optional, Dict, Any
from feign.models import Request, Response, HttpMethod
from feign.interfaces import HttpClient as HttpClientABC


class HttpClient(ABC):
    """Abstract base class for HTTP clients"""

    @abstractmethod
    def execute(self, request: Request) -> Response:
        """Execute a synchronous HTTP request

        Args:
            request: HTTP request to execute

        Returns:
            HTTP response

        Raises:
            Exception: If the request fails
        """
        pass

    @abstractmethod
    async def execute_async(self, request: Request) -> Response:
        """Execute an asynchronous HTTP request

        Args:
            request: HTTP request to execute

        Returns:
            HTTP response

        Raises:
            Exception: If the request fails
        """
        pass

    @abstractmethod
    def is_available(self, url: str) -> bool:
        """Check if a URL is available (returns 200 OK)

        Args:
            url: URL to check

        Returns:
            True if the URL is available, False otherwise
        """
        pass


class HttpClientImpl(HttpClient):
    """HTTP client implementation using httpx"""

    def __init__(self, timeout: int = 30000):
        """Initialize the HTTP client

        Args:
            timeout: Request timeout in milliseconds (default: 30000ms = 30s)
        """
        self.timeout = timeout

    def execute(self, request: Request) -> Response:
        """Execute a synchronous HTTP request using httpx

        Args:
            request: HTTP request to execute

        Returns:
            HTTP response

        Raises:
            Exception: If the request fails
        """
        import httpx

        # Convert request to httpx request
        httpx_request = self._convert_to_httpx(request)

        # Execute request
        with httpx.Client(timeout=self.timeout / 1000) as client:
            httpx_response = client.request(**httpx_request)

        # Convert httpx response to our Response model
        return self._convert_from_httpx(httpx_response)

    async def execute_async(self, request: Request) -> Response:
        """Execute an asynchronous HTTP request using httpx

        Args:
            request: HTTP request to execute

        Returns:
            HTTP response

        Raises:
            Exception: If the request fails
        """
        import httpx

        # Convert request to httpx request
        httpx_request = self._convert_to_httpx(request)

        # Execute async request
        async with httpx.AsyncClient(timeout=self.timeout / 1000) as client:
            httpx_response = await client.request(**httpx_request)

        # Convert httpx response to our Response model
        return self._convert_from_httpx(httpx_response)

    def is_available(self, url: str) -> bool:
        """Check if a URL is available

        Args:
            url: URL to check

        Returns:
            True if the URL is available, False otherwise
        """
        import httpx

        try:
            with httpx.Client(timeout=5) as client:
                response = client.get(url)
                return response.status_code == 200
        except Exception:
            return False

    def _convert_to_httpx(self, request: Request) -> Dict[str, Any]:
        """Convert our Request model to httpx request parameters

        Args:
            request: Our Request model

        Returns:
            Dictionary of httpx request parameters
        """
        import httpx

        httpx_request: Dict[str, Any] = {
            "method": request.method.upper(),
            "url": request.url,
            "headers": request.headers,
        }

        if request.body:
            httpx_request["content"] = request.body

        if request.params:
            httpx_request["params"] = request.params

        return httpx_request

    def _convert_from_httpx(self, httpx_response) -> Response:
        """Convert httpx response to our Response model

        Args:
            httpx_response: httpx Response object

        Returns:
            Our Response model
        """
        # Convert headers (httpx returns them differently)
        headers = dict(httpx_response.headers)

        return Response(
            status_code=httpx_response.status_code,
            headers=headers,
            body=httpx_response.content,
        )
