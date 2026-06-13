"""Tests for DefaultRetryPolicy"""
import pytest
from feign.retry.default_retry_policy import DefaultRetryPolicy


class TestDefaultRetryPolicy:
    def test_default_values(self):
        """Test that default values are set correctly"""
        policy = DefaultRetryPolicy()
        assert policy.max_retries() == 3
        assert policy.retry_interval() == 1000

    def test_can_retry_with_retry_count(self):
        """Test retry logic based on retry count"""
        policy = DefaultRetryPolicy()

        assert policy.can_retry(0, RuntimeError("Error")) is True
        assert policy.can_retry(1, RuntimeError("Error")) is True
        assert policy.can_retry(2, RuntimeError("Error")) is True
        assert policy.can_retry(3, RuntimeError("Error")) is False

    def test_can_retry_with_exception_type(self):
        """Test retry logic based on exception type"""
        policy = DefaultRetryPolicy()

        assert policy.can_retry(0, RuntimeError("Network error")) is True
        assert policy.can_retry(1, ValueError("Validation error")) is False
        assert policy.can_retry(2, RuntimeError("Timeout")) is True

    def test_custom_retry_policy(self):
        """Test custom retry policy configuration"""
        policy = DefaultRetryPolicy()
        policy.max_retries = 5
        policy.retry_interval = 2000

        assert policy.max_retries() == 5
        assert policy.retry_interval() == 2000

    def test_can_retry_with_none_exception(self):
        """Test that no exception results in no retry"""
        policy = DefaultRetryPolicy()
        assert policy.can_retry(0, None) is False
        assert policy.can_retry(2, None) is False
