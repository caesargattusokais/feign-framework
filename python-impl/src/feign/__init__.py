"""Feign Framework Python Implementation"""
from feign.client.feign_client import FeignClient
from feign.client.http_client import HttpClient
from feign.loadbalancer.round_robin import RoundRobinLoadBalancer
from feign.loadbalancer.random import RandomLoadBalancer
from feign.retry.default_retry_policy import DefaultRetryPolicy

__version__ = "1.0.0"
__all__ = [
    "FeignClient",
    "HttpClient",
    "RoundRobinLoadBalancer",
    "RandomLoadBalancer",
    "DefaultRetryPolicy",
]
