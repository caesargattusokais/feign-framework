"""Base abstract class for retry policies"""
from abc import ABC, abstractmethod
from typing import Optional


class RetryPolicy(ABC):
    """Abstract base class for retry policies"""

    @abstractmethod
    def can_retry(self, retry_count: int, last_exception: Optional[Exception]) -> bool:
        """Determine if a retry should be attempted

        Args:
            retry_count: Current retry attempt count (0-based)
            last_exception: The last exception that occurred

        Returns:
            True if retry should be attempted, False otherwise
        """
        pass

    @abstractmethod
    def max_retries(self) -> int:
        """Get the maximum number of retries

        Returns:
            Maximum retry count
        """
        pass

    @abstractmethod
    def retry_interval(self) -> int:
        """Get the retry interval in milliseconds

        Returns:
            Retry interval in milliseconds
        """
        pass
