"""Abstract base classes for Feign framework components"""
from abc import ABC, abstractmethod
from typing import List


class RetryPolicy(ABC):
    """Abstract base class for retry policies"""

    @abstractmethod
    def can_retry(self, retry_count: int, last_exception: Exception) -> bool:
        """Determine if retry should occur

        Args:
            retry_count: Current retry count
            last_exception: Last exception that occurred

        Returns:
            True if retry should occur, False otherwise
        """
        pass

    @abstractmethod
    def max_retries(self) -> int:
        """Get maximum retry count

        Returns:
            Maximum number of retries allowed
        """
        pass

    @abstractmethod
    def retry_interval(self) -> int:
        """Get retry interval in milliseconds

        Returns:
            Delay between retries in milliseconds
        """
        pass


class LoadBalancer(ABC):
    """Abstract base class for load balancers"""

    @abstractmethod
    def select(self, service_name: str) -> str:
        """Select a server for the given service

        Args:
            service_name: Name of the service

        Returns:
            Selected server URL

        Raises:
            Exception: If no servers are available
        """
        pass

    @abstractmethod
    def add_server(self, url: str) -> None:
        """Add a server to the load balancer

        Args:
            url: Server URL to add
        """
        pass

    @abstractmethod
    def remove_server(self, url: str) -> None:
        """Remove a server from the load balancer

        Args:
            url: Server URL to remove
        """
        pass

    @abstractmethod
    def reset(self) -> None:
        """Reset the load balancer state"""
        pass


class HttpClient(ABC):
    """Abstract base class for HTTP clients"""

    @abstractmethod
    def execute(self, request) -> 'Response':
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
    def execute_async(self, request) -> 'Response':
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
