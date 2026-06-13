"""Default retry policy implementation"""
from typing import Optional
from feign.retry.retry_policy import RetryPolicy


class DefaultRetryPolicy(RetryPolicy):
    """Default retry policy that retries on network errors (RuntimeError, OSError)"""

    def __init__(self, max_retries: int = 3, retry_interval: int = 1000):
        """Initialize the retry policy

        Args:
            max_retries: Maximum number of retry attempts (default: 3)
            retry_interval: Delay between retries in milliseconds (default: 1000ms)
        """
        self._max_retries = max_retries
        self._retry_interval = retry_interval

    def max_retries(self) -> int:
        """Get the maximum number of retries

        Returns:
            Maximum retry count
        """
        return self._max_retries

    def retry_interval(self) -> int:
        """Get the retry interval in milliseconds

        Returns:
            Retry interval in milliseconds
        """
        return self._retry_interval

    def can_retry(self, retry_count: int, last_exception: Optional[Exception]) -> bool:
        """Determine if a retry should be attempted

        Args:
            retry_count: Current retry attempt count (0-based)
            last_exception: The last exception that occurred

        Returns:
            True if retry should be attempted, False otherwise
        """
        # Check if we've exceeded max retries
        if retry_count >= self._max_retries:
            return False

        # No exception means no retry
        if last_exception is None:
            return False

        # Can retry on network errors (RuntimeError, OSError)
        return isinstance(last_exception, (RuntimeError, OSError))

    def __setattr__(self, name: str, value: object) -> None:
        """Allow modification of max_retries and retry_interval via properties"""
        if name in ["_max_retries", "_retry_interval"]:
            object.__setattr__(self, name, value)
