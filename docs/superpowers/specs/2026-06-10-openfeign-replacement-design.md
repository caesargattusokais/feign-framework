# OpenFeign 替代框架设计方案

**创建日期：** 2026-06-10
**项目目标：** 创建一个类似 OpenFeign 的声明式 HTTP 客户端框架，支持多种语言和协议
**文档版本：** 1.0

---

## 1. 项目概述

### 1.1 目标
创建一个 OpenFeign 的完整替代品，具有以下特性：
- 声明式 API 设计（注解驱动）
- 支持多种语言（Java、Python 最先实现）
- 支持多种通信协议（HTTP/HTTPS、gRPC、WebSocket）
- 支持异步调用、负载均衡、重试机制

### 1.2 约束条件
- 项目位置：D:/feign-framework
- 核心抽象层使用 Java 定义
- 各语言实现独立模块
- 注解驱动 API

---

## 2. 架构方案

### 2.1 选定架构：核心抽象层 + 语言实现层

**架构图：**

```
┌─────────────────────────────────────────────────────────────┐
│                        Client Code                          │
│  (使用注解声明式地定义 API)                                    │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│                    Annotation Processor                      │
│  (编译时处理注解，生成代理类)                                   │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│                    Dynamic Proxy                            │
│  (运行时创建动态代理，拦截方法调用)                               │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│                    Core Abstraction Layer                    │
│  (核心抽象层：定义统一接口和标准)                               │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐          │
│  │ HttpClient  │  │ GrpcClient  │  │WsClient     │          │
│  └─────────────┘  └─────────────┘  └─────────────┘          │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│                    Load Balancer                            │
│  (负载均衡器：轮询、随机、最少连接等策略)                         │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│                   Implementation Layer                       │
│  Java 实现  │  Python 实现                                    │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 架构说明

**核心抽象层（Java 定义）：**
- 定义核心接口和注解
- 规范各协议的实现标准
- 提供类型安全保证

**实现层：**
- Java 实现模块
- Python 实现模块
- 各语言独立开发和维护

**优势：**
- 职责分离清晰
- 扩展性强，易于添加新语言和协议
- Java 作为核心抽象定义，保证类型安全
- 学习曲线友好

---

## 3. 核心组件设计

### 3.1 注解定义

#### 3.1.1 FeignClient 注解
```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface FeignClient {
    String name() default "";              // 服务名
    String url() default "";               // 服务地址（覆盖 name）
    String contextPath() default "";       // 上下文路径
    String[] path() default {};            // 路径映射

    LoadBalancerType loadBalancer() default LoadBalancerType.ROUND_ROBIN;
    RetryPolicy retry() default @RetryPolicy();

    boolean async() default true;          // 是否异步调用
    int timeout() default 5000;            // 超时时间（毫秒）
}
```

#### 3.1.2 FeignMethod 注解
```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface FeignMethod {
    String name() default "";              // 方法名
    HttpMethod method() default GET;       // HTTP 方法

    String[] path() default {};            // 路径参数
    Map<String, String> headers() default @DefaultMap({});  // 请求头
    Type[] form() default {};              // 表单参数
    String[] query() default {};           // 查询参数
}
```

### 3.2 核心接口

#### 3.2.1 HttpClient 接口
```java
public interface HttpClient {
    <T> T execute(Request request, Class<T> responseType) throws FeignException;

    <T> CompletableFuture<T> executeAsync(Request request, Class<T> responseType);

    boolean isAvailable(String url);
}
```

#### 3.2.2 LoadBalancer 接口
```java
public interface LoadBalancer {
    String select(String serviceName);

    void addServer(String url);
    void removeServer(String url);
    void reset();
}
```

#### 3.2.3 RetryPolicy 接口
```java
public interface RetryPolicy {
    boolean canRetry(int retryCount, Throwable lastException);
    int maxRetries();
    long retryInterval();
}
```

### 3.3 请求/响应模型

```java
public class Request {
    private String method;
    private String url;
    private Map<String, String> headers;
    private Object body;
    private Map<String, String> queryParams;
}

public class Response {
    private int statusCode;
    private Map<String, String> headers;
    private Object body;
    private boolean successful();
}
```

---

## 4. 技术栈选择

### 4.1 Java 模块
- **编译时注解处理：** Java Compiler Tree API (javax.tools)
- **动态代理：** Java Dynamic Proxy
- **JSON 处理：** Jackson / Gson
- **HTTP 客户端：** Apache HttpClient / OkHttp

### 4.2 Python 模块
- **注解处理：** Python 3.7+ Type Hints
- **动态代理：** functools.partial / 动态方法创建
- **JSON 处理：** json / pydantic
- **HTTP 客户端：** httpx / aiohttp

### 4.3 共享组件
- **负载均衡器：** 自实现
- **重试机制：** 自实现（指数退避）

---

## 5. 实现计划（第一阶段）

### 5.1 优先级
1. Java HTTP 客户端实现（基础版）
2. 注解处理器和动态代理
3. 负载均衡器
4. 重试机制

### 5.2 时间预估
- 基础框架搭建：2-3 天
- Java HTTP 客户端：3-5 天
- Python HTTP 客户端：2-3 天
- 高级特性（负载均衡、重试）：2-3 天

---

## 6. 验收标准

### 6.1 基础功能
- [ ] 能够通过注解定义客户端接口
- [ ] 能够调用远程 API
- [ ] 能够序列化/反序列化 JSON
- [ ] 能够处理 HTTP 响应

### 6.2 高级特性
- [ ] 支持异步调用
- [ ] 支持负载均衡
- [ ] 支持重试机制
- [ ] 支持多种协议（HTTP/HTTPS、gRPC、WebSocket）

### 6.3 多语言支持
- [ ] Java 客户端完整实现
- [ ] Python 客户端完整实现

---

## 7. 后续扩展方向

1. 添加 gRPC 协议支持
2. 添加 WebSocket 协议支持
3. 支持更多语言（Go、Node.js 等）
4. 添加熔断机制
5. 添加链路追踪
6. 添加性能监控

---

## 8. 实现细节补充

### 8.1 Python 实现层

**项目结构：**
```
python-impl/
├── client/
│   ├── http_client.py
│   └── feign_client.py
├── loadbalancer/
│   ├── round_robin.py
│   └── random.py
└── retry/
    └── retry_policy.py
```

**Python 类型注解：**
```python
from typing import Optional, Dict, Any, Type, Callable
from dataclasses import dataclass
from enum import Enum
import asyncio

class HttpMethod(Enum):
    GET = "GET"
    POST = "POST"
    PUT = "PUT"
    DELETE = "DELETE"
    PATCH = "PATCH"

@dataclass
class Request:
    method: HttpMethod
    url: str
    headers: Dict[str, str]
    body: Optional[Any] = None
    params: Dict[str, Any] = None

@dataclass
class Response:
    status_code: int
    headers: Dict[str, str]
    body: Any
```

**Python FeignClient 实现：**
```python
from functools import partial
from typing import get_type_hints
import inspect

class FeignClient:
    def __init__(self, name: str, url: str, config: dict = None):
        self.name = name
        self.url = url
        self.config = config or {}
        self._method_map = {}

    def __getattr__(self, name: str) -> Callable:
        """动态方法创建"""
        def method(*args, **kwargs):
            method_name = getattr(self, f"_{name}_method", name)
            method_config = getattr(type(self), f"_{method_name}", {})
            request = self._build_request(method_config, method_name, *args, **kwargs)
            return self._execute(request)
        self._method_map[name] = method
        return method

    def _execute(self, request: Request) -> Any:
        """执行请求"""
        if self.config.get("async", True):
            return asyncio.run(self._execute_async(request))
        else:
            return self._execute_sync(request)

    async def _execute_async(self, request: Request) -> Any:
        """异步执行"""
        import httpx
        async with httpx.AsyncClient() as client:
            response = await client.request(
                method=request.method.value,
                url=request.url,
                headers=request.headers,
                json=request.body,
                params=request.params
            )
            return self._handle_response(response)
```

**Python 使用示例：**
```python
class UserService(FeignClient):
    def __init__(self):
        self._GET_user = FeignMethodConfig(
            method=HttpMethod.GET,
            path=["users", "{id}"],
            async_enabled=False
        )

    def get_user(self, user_id: int) -> dict:
        """获取用户信息"""
        return self.get_user(user_id)
```

### 8.2 装饰器方式（可选）

```python
def feign_method(method=HttpMethod.GET, path=None, async_enabled=True):
    """方法装饰器配置"""
    def decorator(func):
        func._feign_config = {
            "method": method,
            "path": path or [func.__name__],
            "async_enabled": async_enabled
        }
        return func
    return decorator

class UserService(FeignClient):
    def __init__(self):
        super().__init__(name="user-service", url="http://localhost:8080")

    @feign_method(method=HttpMethod.GET, path=["users", "{id}"])
    def get_user(self, user_id: int) -> dict:
        """获取用户信息"""
        pass
```

---

**文档审核状态：** 待用户审核
