# Feign Framework 使用指南

> 一个类 OpenFeign 的声明式 HTTP 客户端框架，支持 Java 和 Python。
> 注解驱动 API、类型化解码、拦截器链、负载均衡、重试、异步、连接池——开箱即用。

---

## 目录

- [快速开始](#快速开始)
- [声明式 API](#声明式-api)
- [响应解码 (Decoder)](#响应解码-decoder)
- [拦截器链 (Interceptor)](#拦截器链-interceptor)
- [负载均衡 (LoadBalancer)](#负载均衡-loadbalancer)
- [重试机制 (Retry)](#重试机制-retry)
- [HTTP 连接池](#http-连接池)
- [协议抽象层 (Protocol)](#协议抽象层-protocol)
- [配置参考](#配置参考)
- [进阶用法](#进阶用法)
- [Python 使用指南](#python-使用指南)
- [项目结构](#项目结构)
- [API 对照表](#api-对照表)

---

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.feign</groupId>
    <artifactId>feign-framework-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>com.feign</groupId>
    <artifactId>feign-framework-java</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>com.feign</groupId>
    <artifactId>feign-framework-processor</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <scope>provided</scope>   <!-- 编译期校验，非必须 -->
</dependency>
```

### 2. 定义接口

```java
import com.feign.framework.annotations.*;

@FeignClient(name = "user-service", url = "http://localhost:8080")
public interface UserService {

    @FeignMethod(method = HttpMethod.GET, path = {"users", "{id}"})
    User getUser(@Path("id") Long id);

    @FeignMethod(method = HttpMethod.POST, path = {"users"})
    User createUser(User user);
}
```

### 3. 调用

```java
UserService service = FeignClientFactory.create(UserService.class);

User user = service.getUser(1L);        // 自动解码 JSON → User
User created = service.createUser(newUser);
```

---

## 声明式 API

### 注解

#### @FeignClient — 接口级别

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `name` | String | `""` | 服务名 |
| `url` | String | `""` | 服务地址 |
| `path` | String[] | `{}` | 路径前缀 |
| `loadBalancer` | LoadBalancerType | `ROUND_ROBIN` | 负载均衡策略 |
| `connectTimeout` | int | `5000` | 连接超时 (ms) |
| `readTimeout` | int | `10000` | 读取超时 (ms) |
| `maxRetries` | int | `3` | 最大重试次数 |
| `retryInterval` | long | `1000` | 重试间隔 (ms) |

#### @FeignMethod — 方法级别

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `method` | HttpMethod | `GET` | HTTP 方法 |
| `path` | String[] | `{}` | 路径片段，自动用 `/` 拼接 |
| `headers` | String[] | `{}` | 请求头，格式 `"Key: Value"` |
| `name` | String | `""` | 方法别名 |

#### @Path — 参数级别

```java
@FeignMethod(method = HttpMethod.GET, path = {"users", "{id}", "posts", "{postId}"})
User getUserPost(@Path("id") Long userId, @Path("postId") Long postId);
// → GET /users/1/posts/42
```

---

## 响应解码 (Decoder)

**核心能力：返回类型化对象，就像 OpenFeign 一样。** 框架自动将 HTTP 响应体解码为目标类型。

### 内置：GsonDecoder（默认）

```java
// 返回类型 → 自动解码
User        getUser(@Path("id") Long id);     // JSON → User
String      getRaw();                          // 原始字符串
Response    getResponse();                     // 不解码
void        delete(@Path("id") Long id);       // 忽略
CompletableFuture<User> getAsync(...);          // 异步解码
```

### 自定义解码器

```java
public class JacksonDecoder implements Decoder {
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Object decode(Response response, Type type) throws Exception {
        if (type == Response.class) return response;
        if (!response.successful()) throw new FeignException(response.getStatusCode(),
            response.getUrl(), response.getBodyAsString());
        return mapper.readValue(response.getBodyAsString(), mapper.constructType(type));
    }
}

// 注册
UserService service = new FeignClientFactory()
    .decoder(new JacksonDecoder())
    .build(UserService.class);
```

---

## 拦截器链 (Interceptor)

拦截器在请求前后执行，支持**自定义排序**。

```java
// ── 定义 ──
class AuthInterceptor implements FeignInterceptor {
    @Override public int order() { return 0; }   // 最先执行

    @Override
    public Request beforeExecute(Request req) {
        req.getHeaders().put("Authorization", "Bearer " + token);
        return req;
    }
}

class LoggingInterceptor implements FeignInterceptor {
    @Override public int order() { return 10; }

    @Override
    public Request beforeExecute(Request req) {
        System.out.println("→ " + req.getMethod() + " " + req.getUrl());
        return req;
    }

    @Override
    public Response afterExecute(Response resp) {
        System.out.println("← " + resp.getStatusCode());
        return resp;
    }

    @Override
    public void onError(Request req, FeignException e) {
        System.err.println("✗ " + e.getMessage());
    }
}
```

### 注册（两种方式）

```java
// 方式 1：实现 order()，任意顺序注册
factory.addInterceptor(new AuthInterceptor());
factory.addInterceptor(new LoggingInterceptor());

// 方式 2：注册时指定顺序
factory.addInterceptor(new AuthInterceptor(), 0);
factory.addInterceptor(new LoggingInterceptor(), 10);
factory.addInterceptor(new MetricsInterceptor(), 20);
```

### 执行顺序

```
beforeExecute:  Auth(0)  →  Logging(10)  →  Metrics(20)    // order 升序
    ↓ HTTP 调用
afterExecute:   Metrics(20)  →  Logging(10)  →  Auth(0)    // order 降序(reverse)
```

---

## 负载均衡 (LoadBalancer)

### 内置策略

- **RoundRobin** — 轮询
- **Random** — 随机
- **LeastConnections** — 最少连接（占位）

### 注解配置

```java
@FeignClient(name = "user-service", url = "http://localhost:8080",
             loadBalancer = LoadBalancerType.ROUND_ROBIN)
```

### 自定义负载均衡器

```java
// 实现 LoadBalancer 接口
public class WeightedLoadBalancer implements LoadBalancer {
    @Override
    public String select(Request request, List<String> servers) {
        // 你的自定义逻辑
        ...
    }
    @Override public void addServer(String url) { }
    @Override public void removeServer(String url) { }
    @Override public void reset() { }
    @Override public LoadBalancerType getType() { return LoadBalancerType.ROUND_ROBIN; }
}

// 注入
new FeignClientFactory()
    .loadBalancer(new WeightedLoadBalancer())
    .build(UserService.class);
```

---

## 重试机制 (Retry)

代理内置重试循环，无需手动编码。

### 配置

```java
@FeignClient(maxRetries = 3, retryInterval = 1000)
```

- 重试条件：RuntimeException、IOException、FeignException
- 可通过 `RetryPolicy` 接口自定义
- 每次重试前等待 `retryInterval` ms

### 手动使用

```java
DefaultRetryPolicy policy = new DefaultRetryPolicy();
policy.setMaxRetries(5);
policy.setRetryInterval(2000);

if (policy.canRetry(new RuntimeException("timeout"), 0)) {
    // 可以重试
}
```

---

## HTTP 连接池

`HttpProtocolHandler` 内置 **PoolingHttpClientConnectionManager**。

### 默认配置

```java
// maxTotal=200, maxPerRoute=20
new HttpProtocolHandler(5000, 10000);
```

### 自定义连接池

```java
new HttpProtocolHandler(
    5000,   // connectTimeout ms
    10000,  // readTimeout ms
    500,    // maxTotal connections
    50      // maxPerRoute connections
);
```

### 完全自定义 HttpClient

```java
CloseableHttpClient myClient = HttpClients.custom()
    .setConnectionManager(new PoolingHttpClientConnectionManager())
    .setProxy(new HttpHost("proxy", 8080))
    .setDefaultCredentialsProvider(credsProvider)
    .build();

new FeignClientFactory()
    .protocolHandler(new HttpProtocolHandler(myClient, 5000, 10000))
    .build(UserService.class);
```

---

## 协议抽象层 (Protocol)

为未来 gRPC、WebSocket 预留的扩展点。

```
ProtocolHandler (interface)
├── HttpProtocolHandler     ← 当前实现
├── GrpcProtocolHandler     ← 待实现
└── WebSocketProtocolHandler ← 待实现
```

```java
// 实现 ProtocolHandler 即可支持新协议
public class GrpcProtocolHandler implements ProtocolHandler {
    @Override public String scheme() { return "grpc"; }
    @Override public Response execute(Request request) { ... }
    @Override public CompletableFuture<Response> executeAsync(Request request) { ... }
    @Override public boolean isAvailable(String url) { ... }
}
```

---

## 配置参考

### 完整构建链

```java
UserService service = new FeignClientFactory()
    .decoder(new JacksonDecoder())                        // 响应解码器
    .protocolHandler(new HttpProtocolHandler(             // HTTP 客户端 + 连接池
        5000, 10000, 200, 20))
    .loadBalancer(new MyLoadBalancer())                   // 自定义负载均衡
    .addInterceptor(new AuthInterceptor(), 0)              // 拦截器链
    .addInterceptor(new LoggingInterceptor(), 10)
    .addInterceptor(new MetricsInterceptor(), 20)
    .build(UserService.class);
```

### 静态快捷方式

```java
// 最简
UserService svc = FeignClientFactory.create(UserService.class);

// URL 覆盖
UserService svc = FeignClientFactory.create("http://prod:8080", UserService.class);
```

### @FeignClient 完整配置

```java
@FeignClient(
    name = "user-service",
    url = "http://localhost:8080/api",
    loadBalancer = LoadBalancerType.ROUND_ROBIN,
    connectTimeout = 5000,
    readTimeout = 10000,
    maxRetries = 3,
    retryInterval = 1000
)
public interface UserService {
    @FeignMethod(method = HttpMethod.GET, path = {"users", "{id}"})
    User getUser(@Path("id") Long id);

    @FeignMethod(method = HttpMethod.GET, path = {"users", "{id}"})
    CompletableFuture<User> getUserAsync(@Path("id") Long id);
}
```

---

## 进阶用法

### 执行管道

```
call  →  build request  →  interceptors(before)  →  load balancer
      →  retry loop  →  protocolHandler  →  decoder  →  interceptors(after)
      →  return typed object
```

### 异常处理

```java
try {
    User user = service.getUser(999L);
} catch (FeignException e) {
    e.getStatus();    // HTTP 状态码
    e.getMethod();    // 请求方法
    e.getUrl();       // 请求 URL
    e.getMessage();   // 错误详情
}
```

### 异步调用

```java
CompletableFuture<User> future = service.getUserAsync(1L);
future.thenAccept(user -> System.out.println(user.getName()))
      .exceptionally(e -> { log.error("Failed", e); return null; });
```

### 直接使用 HttpClient（不走代理）

```java
HttpClientImpl client = new HttpClientImpl(new HttpClientConfig());
Request req = Request.of(HttpMethod.GET, "https://api.example.com/users/1",
                          new HashMap<>(), null, new HashMap<>());
Response resp = client.execute(req);
```

---

## Python 使用指南

### 安装

```bash
pip install -e python-impl/
```

### 定义和调用

```python
from feign import FeignClient

class UserService(FeignClient):
    def __init__(self):
        super().__init__(name="user-service", url="http://localhost:8080/api",
                         timeout=10000)

    def get_user(self, user_id: int) -> dict:
        """GET /api/user"""
        pass

    async def create_user(self, data: dict) -> dict:
        """POST /api/user"""
        pass

# 同步
service = UserService()
user = service.get_user(1)

# 异步
import asyncio
result = asyncio.run(service.create_user({"name": "张三"}))
```

### 方法名 → HTTP 方法映射

| 前缀 | HTTP |
|------|------|
| `get_*` | GET |
| `post_*` | POST |
| `put_*` | PUT |
| `delete_*` | DELETE |
| `patch_*` | PATCH |

### Python 自定义配置

```python
from feign.loadbalancer.random import RandomLoadBalancer
from feign.retry.default_retry_policy import DefaultRetryPolicy

class OrderService(FeignClient):
    def __init__(self):
        super().__init__(
            name="order-service",
            url="http://localhost:8080",
            timeout=10000,
            headers={"Authorization": "Bearer xxx"}
        )
```

---

## 项目结构

```
feign-framework/
│
├── core/                          # 核心抽象层
│   ├── annotations/               # @FeignClient @FeignMethod @Path
│   ├── http/                      # Request, Response, HttpMethod
│   ├── client/                    # HttpClient 接口
│   ├── codec/                     # Decoder 接口
│   ├── interceptor/               # FeignInterceptor 接口
│   ├── loadbalancer/              # LoadBalancer, LoadBalancerType
│   ├── protocol/                  # ProtocolHandler 接口
│   ├── retry/                     # RetryPolicy 接口
│   ├── config/                    # FeignConfig
│   └── FeignException.java
│
├── java-impl/                     # Java 实现
│   ├── client/                    # HttpClientImpl
│   ├── codec/                     # GsonDecoder
│   ├── loadbalancer/              # RoundRobin, Random
│   ├── protocol/                  # HttpProtocolHandler (连接池)
│   └── retry/                     # DefaultRetryPolicy
│
├── java-processor/                # 编译期校验 + 运行时代理
│   └── processor/
│       ├── FeignClientProxy       # 核心 InvocationHandler（完整执行管道）
│       ├── FeignClientFactory     # 工厂（流式 API + 依赖注入）
│       ├── FeignClientMetadata    # 注解元数据
│       └── FeignClientProcessor   # JSR 269 编译期校验
│
├── python-impl/                   # Python 实现
│   └── src/feign/                 # models, interfaces, client, loadbalancer, retry
│
├── examples/                      # 示例代码
│   ├── UserService.java
│   ├── FeignClientDemo.java
│   └── AdvancedExample.java       # 完整功能演示
│
└── docs/                          # 文档
    ├── USAGE.md                   # 使用指南
    ├── FIXES.md                   # 修复历史
    └── superpowers/               # 设计文档 + 实施计划
```

---

## API 对照表

### Java 核心类

| 类别 | 接口/抽象 | 默认实现 | 包路径 |
|------|----------|---------|--------|
| HTTP 客户端 | `HttpClient` | `HttpClientImpl` | `client` |
| 协议处理器 | `ProtocolHandler` | `HttpProtocolHandler` | `protocol` |
| 响应解码器 | `Decoder` | `GsonDecoder` | `codec` |
| 拦截器 | `FeignInterceptor` | — | `interceptor` |
| 负载均衡 | `LoadBalancer` | `RoundRobin` `Random` | `loadbalancer` |
| 重试策略 | `RetryPolicy` | `DefaultRetryPolicy` | `retry` |
| 动态代理 | — | `FeignClientProxy` | `processor` |

### Java ↔ Python

| 功能 | Java | Python |
|------|------|--------|
| 声明式客户端 | `@FeignClient` 注解 | `FeignClient` 基类 |
| 请求模型 | `Request.of(...)` | `Request` dataclass |
| 响应模型 | `Response.of(...)` | `Response` dataclass |
| HTTP 客户端 | `HttpClientImpl` | `HttpClientImpl` |
| 轮询负载均衡 | `RoundRobinLoadBalancer` | `RoundRobinLoadBalancer` |
| 随机负载均衡 | `RandomLoadBalancer` | `RandomLoadBalancer` |
| 重试策略 | `DefaultRetryPolicy` | `DefaultRetryPolicy` |
| 异常 | `FeignException` | Python 内置异常 |

---

## 关于 FeignClientProcessor

`FeignClientProcessor` 是 JSR 269 编译期注解处理器，当前负责**编译期校验**：

- 检查 `@FeignClient` 是否标注在 `interface` 上
- 统计 `@FeignMethod` 方法数量
- 在编译期输出诊断信息

**实际的代理和请求执行由 `FeignClientProxy`（运行时 InvocationHandler）完成。**
Processor 是可选的（`<scope>provided</scope>`），删除它不影响运行。
