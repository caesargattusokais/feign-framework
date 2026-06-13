# Feign Framework Python Implementation

Python implementation of the Feign framework - a declarative web service client.

## Features

- Declarative API definition using Python class methods
- Built-in load balancing (Round Robin, Random)
- Retry policy support for transient failures
- Synchronous and asynchronous HTTP clients
- Automatic method mapping to HTTP operations (GET, POST, PUT, DELETE, PATCH)
- Type hints support for better IDE support

## Installation

```bash
cd python-impl
pip install -e .
```

Or install with development dependencies:

```bash
pip install -e ".[dev]"
```

## Quick Start

```python
from feign import FeignClient

class UserService(FeignClient):
    def __init__(self):
        config = FeignClientConfig(
            name="user-service",
            url="http://localhost:8080",
            load_balancer_type="ROUND_ROBIN"
        )
        super().__init__(config)

    def get_user(self, user_id: int) -> dict:
        """Get user by ID - automatically maps to GET /get_user/{user_id}"""
        return self.get_user(user_id)

    def create_user(self, user_data: dict) -> dict:
        """Create user - automatically maps to POST /create_user"""
        return self.create_user(user_data)

    def update_user(self, user_id: int, user_data: dict) -> dict:
        """Update user - automatically maps to PUT /update_user/{user_id}"""
        return self.update_user(user_id, user_data)

    def delete_user(self, user_id: int) -> dict:
        """Delete user - automatically maps to DELETE /delete_user/{user_id}"""
        return self.delete_user(user_id)
```

## Usage Examples

### 1. Synchronous Client

```python
from feign import FeignClient

class UserService(FeignClient):
    def __init__(self):
        super().__init__(
            name="user-service",
            url="http://localhost:8080"
        )

    def get_user(self, user_id: int) -> dict:
        return self.get_user(user_id)

user_service = UserService()

# Get user (URL: http://localhost:8080/get_user/123)
user = user_service.get_user(user_id=123)

# Create user (URL: http://localhost:8080/create_user)
user = user_service.create_user({"name": "John", "email": "john@example.com"})

# Update user (URL: http://localhost:8080/update_user/456)
user = user_service.update_user(
    user_id=456,
    user_data={"name": "Jane", "email": "jane@example.com"}
)

# Delete user (URL: http://localhost:8080/delete_user/789)
user = user_service.delete_user(user_id=789)
```

### 2. Asynchronous Client

```python
from feign import FeignClient

class AsyncUserService(FeignClient):
    def __init__(self):
        super().__init__(
            name="async-user-service",
            url="http://localhost:8080",
            timeout=50000  # 50 seconds
        )

    async def get_user_async(self, user_id: int) -> dict:
        return await self.get_user_async(user_id)

async def main():
    user_service = AsyncUserService()

    # Get user asynchronously
    user = await user_service.get_user_async(user_id=123)

asyncio.run(main())
```

### 3. Different Load Balancers

```python
from feign import FeignClient

# Round Robin (default)
class UserService1(FeignClient):
    def __init__(self):
        super().__init__(
            name="service",
            url="http://localhost:8080",
            load_balancer_type="ROUND_ROBIN"
        )

# Random
class UserService2(FeignClient):
    def __init__(self):
        super().__init__(
            name="service",
            url="http://localhost:8080",
            load_balancer_type="RANDOM"
        )
```

### 4. Custom Configuration

```python
from feign import FeignClient
from feign.models import FeignClientConfig, DefaultRetryPolicy

class CustomUserService(FeignClient):
    def __init__(self):
        config = FeignClientConfig(
            name="custom-service",
            url="http://localhost:8080",
            load_balancer_type="ROUND_ROBIN",
            retry_policy=DefaultRetryPolicy(max_retries=5, retry_interval=2000),
            timeout=60000
        )
        super().__init__(config)
```

## HTTP Method Mapping

The framework automatically maps method names to HTTP operations:

| Method Name Pattern | HTTP Method | Example URL |
|---------------------|-------------|-------------|
| `get_*`             | GET         | `/get_user/123` |
| `post_*`            | POST        | `/create_user` |
| `put_*`             | PUT         | `/update_user/456` |
| `delete_*`          | DELETE      | `/delete_user/789` |
| `patch_*`           | PATCH       | `/patch_user/901` |

## API Reference

### Core Components

#### FeignClient

Base class for creating Feign clients.

**Parameters:**
- `config` (FeignClientConfig, optional): Client configuration

**Methods:**
- `get_user(*args, **kwargs)`: Dynamically creates HTTP request methods
- `_execute_request(request)`: Executes HTTP request
- `_determine_http_method(method_name)`: Determines HTTP method from method name
- `_register_server(url)`: Registers server with load balancer

#### HttpClient

HTTP client interface.

**Methods:**
- `execute(request)`: Execute synchronous HTTP request
- `execute_async(request)`: Execute asynchronous HTTP request
- `is_available(url)`: Check if URL is available

#### Load Balancers

**RoundRobinLoadBalancer**
- Distributes requests sequentially among servers

**RandomLoadBalancer**
- Selects servers uniformly at random

#### Retry Policy

**DefaultRetryPolicy**
- Retries on network errors (RuntimeError, OSError)
- Configurable max retries and retry interval

## Running Tests

```bash
cd python-impl
pytest tests/ -v
```

Run with coverage:

```bash
pytest tests/ --cov=src/feign --cov-report=html
```

## Architecture

### Module Structure

```
python-impl/
├── src/
│   └── feign/
│       ├── __init__.py
│       ├── client/
│       │   ├── __init__.py
│       │   ├── http_client.py
│       │   └── feign_client.py
│       ├── loadbalancer/
│       │   ├── __init__.py
│       │   ├── load_balancer.py
│       │   ├── round_robin.py
│       │   └── random.py
│       ├── retry/
│       │   ├── __init__.py
│       │   ├── retry_policy.py
│       │   └── default_retry_policy.py
│       └── models.py
├── tests/
│   ├── client/
│   ├── loadbalancer/
│   └── retry/
├── examples/
├── pyproject.toml
└── README.md
```

### Design Patterns

- **Dynamic Method Creation**: Uses `__getattr__` and `functools.partial` to create API methods dynamically
- **Strategy Pattern**: Load balancers implement the LoadBalancer interface
- **Template Method**: HttpClient provides base implementation for synchronous/async execution
- **Dependency Injection**: Configuration is injected into FeignClient

## Dependencies

- `httpx>=0.24.0` - Modern HTTP client with async support
- `typing` - Type hints
- `functools` - Dynamic method creation
- `dataclasses` - Data models

## Future Enhancements

- Connection pooling
- Request/response interceptors
- Circuit breaker pattern
- Request timeout per endpoint
- Query parameter mapping
- Path variable substitution
- Better error handling and validation
- Support for gRPC and other protocols

## License

MIT License
