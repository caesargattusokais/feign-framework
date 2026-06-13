"""Load balancer implementations"""
from feign.loadbalancer.round_robin import RoundRobinLoadBalancer
from feign.loadbalancer.random import RandomLoadBalancer

__all__ = ["RoundRobinLoadBalancer", "RandomLoadBalancer"]
