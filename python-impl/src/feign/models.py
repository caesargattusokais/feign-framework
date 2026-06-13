"""Core data models for Feign framework"""
from dataclasses import dataclass
from typing import Optional, Dict, Any
from enum import Enum


class HttpMethod(Enum):
    """HTTP methods"""
    GET = "GET"
    POST = "POST"
    PUT = "PUT"
    DELETE = "DELETE"
    PATCH = "PATCH"


@dataclass
class Request:
    """HTTP request model"""
    method: HttpMethod
    url: str
    headers: Dict[str, str]
    body: Optional[Any] = None
    params: Dict[str, Any] = None

    def __post_init__(self):
        """Convert method to uppercase string if needed"""
        if isinstance(self.method, str):
            self.method = HttpMethod(self.method.upper())


@dataclass
class Response:
    """HTTP response model"""
    status_code: int
    headers: Dict[str, str]
    body: Optional[Any] = None

    def successful(self) -> bool:
        """Check if response was successful (2xx status codes)"""
        return 200 <= self.status_code < 300

    def json(self) -> Any:
        """Parse response body as JSON if it's a string"""
        if isinstance(self.body, str):
            import json
            return json.loads(self.body)
        return self.body
