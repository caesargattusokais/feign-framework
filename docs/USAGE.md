# Feign Framework 使用指南

> 一个类 OpenFeign 的声明式 HTTP 客户端框架，支持 Java 和 Python，提供注解驱动 API、负载均衡、重试机制和异步调用。

---

## 目录

1. [快速开始](#快速开始)
2. [Java 使用指南](#java-使用指南)
3. [Python 使用指南](#python-使用指南)
4. [负载均衡](#负载均衡)
5. [重试机制](#重试机制)
6. [配置管理](#配置管理)
7. [进阶用法](#进阶用法)

---

## 快速开始

### Java 项目

**1. 添加 Maven 依赖**

```xml
<!-- pom.xml -->
<dependencies>
    <!-- 核心抽象层 -->
    <dependency>
        <groupId>com.feign</groupId>
        <artifactId>feign-framework-core</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>

    <!-- Java 实现层 -->
    <dependency>
        <groupId>com.feign</groupId>
        <artifactId>feign-framework-java</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>

    <!-- 注解处理器 -->
    <dependency>
        <groupId>com.feign</groupId>
        <artifactId>feign-framework-processor</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

**2. 定义 Feign 客户端接口**

```java
import com.feign.framework.annotations.FeignClient;
import com.feign.framework.annotations.FeignMethod;
import com.feign.framework.http.HttpMethod;

@FeignClient(name = "user-service", url = "http://localhost:8080")
public interface UserService {

    @FeignMethod(method = HttpMethod.GET, path = {"users", "{id}"})
    String getUser(Long id);

    @FeignMethod(method = HttpMethod.POST, path = {"users"})
    String createUser(String userData);
}
```

**3. 创建代理实例并调用**

```java
import com.feign.processor.FeignClientFactory;

public class App {
    public static void main(String[] args) {
        // 创建代理实例
        UserService userService = FeignClientFactory.create(UserService.class);

        // 调用远程 API — 就像调用本地方法一样！
        String userJson = userService.getUser(1L);
        System.out.println("User: " + userJson);

        String newUser = userService.createUser("{\"name\":\"张三\",\"email\":\"zhangsan@example.com\"}");
        System.out.println("Created: " + newUser);
    }
}
```

### Python 项目

**1. 安装**

```bash
pip install -e python-impl/
# 依赖: httpx, pytest (开发环境)
```

**2. 定义 Feign 客户端**

```python
from feign import FeignClient

class UserService(FeignClient):
    def __init__(self):
        super().__init__(
            name="user-service",
            url="http://localhost:8080"
        )

    def get_user(self, user_id: int) -> dict:
        """GET /users/{id}"""
        pass  # 方法体由框架自动处理

    async def create_user(self, user_data: dict) -> dict:
        """POST /users"""
        pass  # 异步方法自动处理
```

**3. 使用客户端**

```python
import asyncio
from user_service import UserService

# 创建客户端
service = UserService()

# 同步调用
user = service.get_user(1)
print(f"User: {user}")

# 异步调用
async def main():
    new_user = await service.create_user({"name": "张三"})
    print(f"Created: {new_user}")

asyncio.run(main())
```

---

## Java 使用指南

### 基本用法

#### 定义客户端接口

```java
@FeignClient(name = "api-service", url = "https://api.example.com")
public interface ApiService {

    // GET 请求
    @FeignMethod(method = HttpMethod.GET, path = {"users", "{id}"})
    String getUser(@Path("id") Long id);

    // POST 请求
    @FeignMethod(method = HttpMethod.POST, path = {"users"})
    String createUser(String body);

    // PUT 请求
    @FeignMethod(method = HttpMethod.PUT, path = {"users", "{id}"})
    String updateUser(@Path("id") Long id, String body);

    // DELETE 请求
    @FeignMethod(method = HttpMethod.DELETE, path = {"users", "{id}"})
    String deleteUser(@Path("id") Long id);
}
```

#### 使用 HttpClientImpl 直接调用

```java
import com.feign.framework.client.HttpClientImpl;
import com.feign.framework.client.HttpClientConfig;
import com.feign.framework.http.Request;
import com.feign.framework.http.HttpMethod;
import com.feign.framework.Response;
import java.util.HashMap;

public class DirectCallExample {
    public static void main(String[] args) throws Exception {
        // 创建 HTTP 客户端
        HttpClientConfig config = new HttpClientConfig();
        config.setConnectTimeout(5000);
        config.setReadTimeout(10000);

        HttpClientImpl client = new HttpClientImpl(config);

        // 构建请求
        Request request = Request.of(
            HttpMethod.GET,
            "https://jsonplaceholder.typicode.com/users/1",
            new HashMap<>(),
            null,
            new HashMap<>()
        );

        // 执行请求
        Response response = client.execute(request);
        System.out.println("Status: " + response.getStatusCode());
        System.out.println("Body: " + response.getBodyAsString());

        // 异步执行
        client.executeAsync(request).thenAccept(resp -> {
            System.out.println("Async result: " + resp.getBodyAsString());
        });
    }
}
```

### @FeignClient 注解参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `name` | String | `""` | 服务名称，用于服务发现 |
| `url` | String | `""` | 服务地址 |
| `path` | String[] | `{}` | 路径前缀 |
| `loadBalancer` | LoadBalancerType | `ROUND_ROBIN` | 负载均衡策略 |
| `timeout` | int | `5000` | 请求超时（毫秒） |

### @FeignMethod 注解参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `method` | HttpMethod | `GET` | HTTP 方法 |
| `path` | String[] | `{}` | 请求路径 |
| `headers` | String[] | `{}` | 请求头（格式：`"Key: Value"`） |
| `name` | String | `""` | 方法别名 |

### HTTP 方法枚举

```java
public enum HttpMethod {
    GET,     // 获取资源
    POST,    // 创建资源
    PUT,     // 更新资源
    DELETE,  // 删除资源
    PATCH,   // 部分更新
    HEAD,    // 获取头部信息
    OPTIONS, // 获取支持的选项
    TRACE    // 追踪请求
}
```

---

## Python 使用指南

### 基本用法

#### 定义客户端类

```python
from feign import FeignClient
from feign.models import HttpMethod

class ProductService(FeignClient):
    def __init__(self):
        super().__init__(
            name="product-service",
            url="http://localhost:8080/api"
        )

    def list_products(self) -> list:
        """GET /api/products"""
        pass

    def get_product(self, product_id: int) -> dict:
        """GET /api/products/{id}"""
        pass

    def create_product(self, product_data: dict) -> dict:
        """POST /api/products"""
        pass

    async def delete_product(self, product_id: int) -> dict:
        """DELETE /api/products/{id}"""
        pass
```

#### HTTP 方法自动映射

框架根据方法名前缀自动推断 HTTP 方法：

| 方法名前缀 | HTTP 方法 | URL 模式 | 示例 |
|-----------|-----------|---------|------|
| `get_*` | GET | `/{method_name}` | `get_user()` → GET /user |
| `post_*` | POST | `/{method_name}` | `post_user()` → POST /user |
| `put_*` | PUT | `/{method_name}` | `put_user()` → PUT /user |
| `delete_*` | DELETE | `/{method_name}` | `delete_user()` → DELETE /user |
| `patch_*` | PATCH | `/{method_name}` | `patch_user()` → PATCH /user |

### 使用 FeignClientConfig

```python
from feign import FeignClient
from feign.models import LoadBalancerType

class OrderService(FeignClient):
    def __init__(self):
        super().__init__(
            name="order-service",
            url="http://localhost:8080/api",
            timeout=10000,
            headers={"Content-Type": "application/json"},
            load_balancer=LoadBalancerType.RANDOM
        )

    def create_order(self, order: dict) -> dict:
        pass
```

### 直接使用 HttpClientImpl

```python
from feign.client.http_client import HttpClientImpl
from feign.models import Request, HttpMethod
import asyncio

# 同步请求
client = HttpClientImpl(timeout=5000)
request = Request(
    method=HttpMethod.GET,
    url="https://jsonplaceholder.typicode.com/users/1",
    headers={"Accept": "application/json"},
    body=None,
    params={}
)
response = client.execute(request)
print(f"Status: {response.status_code}")
print(f"Body: {response.body}")

# 异步请求
async def async_example():
    response = await client.execute_async(request)
    print(f"Async Status: {response.status_code}")

asyncio.run(async_example())
```

---

## 负载均衡

### Java

```java
import com.feign.framework.loadbalancer.RoundRobinLoadBalancer;
import com.feign.framework.loadbalancer.RandomLoadBalancer;
import com.feign.framework.http.Request;
import java.util.Arrays;
import java.util.List;

// 轮询负载均衡
RoundRobinLoadBalancer roundRobin = new RoundRobinLoadBalancer();
roundRobin.addServer("http://server1:8080");
roundRobin.addServer("http://server2:8080");
roundRobin.addServer("http://server3:8080");

List<String> servers = Arrays.asList(
    "http://server1:8080",
    "http://server2:8080",
    "http://server3:8080"
);

for (int i = 0; i < 10; i++) {
    String server = roundRobin.select(null, servers);
    System.out.println("请求 " + i + " → " + server);
}

// 随机负载均衡
RandomLoadBalancer random = new RandomLoadBalancer();
String randomServer = random.select(null, servers);
System.out.println("随机选择: " + randomServer);
```

### Python

```python
from feign.loadbalancer.round_robin import RoundRobinLoadBalancer
from feign.loadbalancer.random import RandomLoadBalancer

# 轮询负载均衡
rr = RoundRobinLoadBalancer()
rr.add_server("http://server1:8080")
rr.add_server("http://server2:8080")
rr.add_server("http://server3:8080")

for i in range(10):
    server = rr.select("my-service")
    print(f"请求 {i} → {server}")

# 随机负载均衡
rand = RandomLoadBalancer()
servers = ["http://server1:8080", "http://server2:8080"]
rand.select(None, servers)

# 在 FeignClient 中使用
class UserService(FeignClient):
    def __init__(self):
        super().__init__(
            name="user-service",
            url="http://localhost:8080",
            load_balancer=RandomLoadBalancer()
        )

    def get_user(self, user_id: int) -> dict:
        pass
```

---

## 重试机制

### Java

```java
import com.feign.framework.retry.DefaultRetryPolicy;

// 默认重试策略（最多重试 3 次，间隔 1 秒）
DefaultRetryPolicy policy = new DefaultRetryPolicy();

// 自定义重试策略
DefaultRetryPolicy customPolicy = new DefaultRetryPolicy();
customPolicy.setMaxRetries(5);        // 最多重试 5 次
customPolicy.setRetryInterval(2000);  // 间隔 2 秒
customPolicy.setEnabled(true);        // 启用重试

// 检查是否可以重试
if (customPolicy.canRetry(new RuntimeException("网络错误"), 0)) {
    System.out.println("可以重试...");
    System.out.println("最大重试次数: " + customPolicy.getMaxRetries());
    System.out.println("重试间隔: " + customPolicy.getRetryInterval() + "ms");
    System.out.println("负载均衡: " + customPolicy.getLoadBalancerType());
}
```

### Python

```python
from feign.retry.default_retry_policy import DefaultRetryPolicy

# 默认重试策略
policy = DefaultRetryPolicy()

# 自定义重试策略
policy.max_retries = 5
policy.retry_interval = 2000
policy.enabled = True

# 检查是否可以重试
if policy.can_retry(0, RuntimeError("网络错误")):
    print("可以重试...")
    print(f"最大重试次数: {policy.max_retries()}")
    print(f"重试间隔: {policy.retry_interval()}ms")

# 重试逻辑
max_retries = policy.max_retries()
for retry_count in range(max_retries):
    try:
        response = client.execute(request)
        if response.successful():
            break
    except Exception as e:
        if not policy.can_retry(retry_count, e):
            raise
        print(f"重试 {retry_count + 1}/{max_retries}...")
```

---

## 配置管理

### Java - HttpClientConfig

```java
import com.feign.framework.client.HttpClientConfig;

HttpClientConfig config = new HttpClientConfig();
config.setConnectTimeout(5000);     // 连接超时 5 秒
config.setReadTimeout(10000);       // 读取超时 10 秒
config.setEnableLogging(true);      // 启用日志
```

### Java - FeignConfig

```java
import com.feign.framework.config.FeignConfig;
import com.feign.framework.client.HttpClientImpl;

FeignConfig config = new FeignConfig("http://localhost:8080");
config.setConnectTimeout(3000);
config.setReadTimeout(7000);

// 使用 Builder 模式
FeignConfig builderConfig = new FeignConfig()
    .withBaseUrl("http://localhost:8080")
    .withConnectTimeout(5000)
    .withReadTimeout(10000)
    .withRetryEnabled(true)
    .withMaxRetries(3)
    .withDefaultHeader("Authorization", "Bearer token123");
```

### Python - FeignClientConfig

```python
from feign.models import FeignClientConfig

# 通过 __init__ 配置
class UserService(FeignClient):
    def __init__(self):
        super().__init__(
            name="user-service",
            url="http://localhost:8080/api",
            timeout=10000,
            headers={
                "Content-Type": "application/json",
                "Authorization": "Bearer token123"
            }
        )

    def get_user(self, user_id: int) -> dict:
        pass
```

---

## 响应解码（Decoder）

OpenFeign 的核心能力：返回**类型化对象**而非原始 Response。

### 内置解码器

```java
// GsonDecoder — 默认，自动将 JSON 反序列化为目标类型
public interface UserService {
    @FeignMethod(method = HttpMethod.GET, path = {"users", "{id}"})
    User getUser(@Path("id") Long id);  // 返回 User 对象，不是 Response！
}
```

### 自定义解码器

```java
// Jackson 解码器示例
public class JacksonDecoder implements Decoder {
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Object decode(Response response, Type type) throws Exception {
        if (type == Response.class) return response;
        if (!response.successful()) throw new FeignException(...);
        JavaType javaType = mapper.constructType(type);
        return mapper.readValue(response.getBodyAsString(), javaType);
    }
}

// 注册
UserService service = new FeignClientFactory()
    .decoder(new JacksonDecoder())
    .build(UserService.class);
```

### 返回类型支持

| 接口返回类型 | 行为 |
|------------|------|
| `User` | decoder 将 JSON → User 对象 |
| `Response` | 返回原始 Response（不解码） |
| `String` | 返回 body 字符串 |
| `void` | 返回 null |
| `CompletableFuture<User>` | 异步 + 解码 |

---

## 自定义负载均衡

```java
// 实现 LoadBalancer 接口
public class WeightedLoadBalancer implements LoadBalancer {
    @Override
    public String select(Request request, List<String> servers) {
        // 你的自定义选择逻辑
        return servers.get(0);
    }
    @Override public void addServer(String url) { }
    @Override public void removeServer(String url) { }
    @Override public void reset() { }
    @Override public LoadBalancerType getType() { return LoadBalancerType.ROUND_ROBIN; }
}

// 注入自定义负载均衡器
UserService service = new FeignClientFactory()
    .loadBalancer(new WeightedLoadBalancer())
    .build(UserService.class);
```

---

## HTTP 连接池

```java
// 默认连接池：200 最大连接，20 每路由
new HttpProtocolHandler(5000, 10000);

// 自定义连接池大小
new HttpProtocolHandler(
    5000,   // connectTimeout ms
    10000,  // readTimeout ms
    500,    // maxTotal connections
    50      // maxPerRoute connections
);

// 注入自定义 HttpClient（完全控制）
CloseableHttpClient myClient = HttpClients.custom()
    .setConnectionManager(new PoolingHttpClientConnectionManager())
    .setDefaultCredentialsProvider(myProvider)
    .build();

UserService service = new FeignClientFactory()
    .protocolHandler(new HttpProtocolHandler(myClient, 5000, 10000))
    .build(UserService.class);
```

---

## 拦截器排序

```java
// 方式 1: 实现 order() 方法
class AuthInterceptor implements FeignInterceptor {
    @Override public int order() { return 0; }  // 最先执行
    ...
}

// 方式 2: 注册时指定顺序
new FeignClientFactory()
    .addInterceptor(new AuthInterceptor(), 0)     // order=0, 最先
    .addInterceptor(new LoggingInterceptor(), 10) // order=10
    .addInterceptor(new MetricsInterceptor(), 20) // order=20, 最后
    .build(UserService.class);

// beforeExecute: Auth(0) → Logging(10) → Metrics(20)
// afterExecute:  Metrics(20) → Logging(10) → Auth(0)  (reverse)
```

---

## 进阶用法

### 1. 结合代理和 HTTP 客户端

```java
// Java: 创建代理后用 HttpClientImpl 执行请求
import com.feign.framework.client.HttpClientImpl;
import com.feign.framework.client.HttpClientConfig;
import com.feign.processor.FeignClientFactory;

UserService proxy = FeignClientFactory.create(UserService.class);
String result = proxy.getUser(1L);
// 代理内部使用 FeignClientProxy 构建 Request，然后交给 HttpClientImpl 执行
```

### 2. 自定义异常处理

```java
import com.feign.framework.FeignException;

try {
    String result = userService.getUser(999L);
} catch (FeignException e) {
    System.out.println("状态码: " + e.getStatus());
    System.out.println("请求方法: " + e.getMethod());
    System.out.println("请求 URL: " + e.getUrl());
    System.out.println("错误信息: " + e.getMessage());
}
```

### 3. 异步批量调用

```python
import asyncio
from feign.client.http_client import HttpClientImpl
from feign.models import Request, HttpMethod

async def fetch_all_users(client, user_ids):
    tasks = []
    for uid in user_ids:
        request = Request(
            method=HttpMethod.GET,
            url=f"https://api.example.com/users/{uid}",
            headers={},
            body=None,
            params={}
        )
        tasks.append(client.execute_async(request))
    return await asyncio.gather(*tasks)

# 使用
async def main():
    client = HttpClientImpl(timeout=5000)
    results = await fetch_all_users(client, [1, 2, 3, 4, 5])
    for response in results:
        print(f"用户: {response.json()}")

asyncio.run(main())
```

```java
// Java: 异步批量调用
import java.util.concurrent.CompletableFuture;
import java.util.List;

HttpClientImpl client = new HttpClientImpl(new HttpClientConfig());

List<CompletableFuture<Response>> futures = List.of(1L, 2L, 3L, 4L, 5L).stream()
    .map(id -> Request.of(
        HttpMethod.GET,
        "https://api.example.com/users/" + id,
        new HashMap<>(),
        null,
        new HashMap<>()
    ))
    .map(request -> {
        try {
            return client.executeAsync(request);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    })
    .collect(java.util.stream.Collectors.toList());

CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

futures.forEach(f -> {
    try {
        Response response = f.get();
        System.out.println("响应: " + response.getBodyAsString());
    } catch (Exception e) {
        e.printStackTrace();
    }
});
```

### 4. 请求/响应拦截

```java
// Java: 包装 HttpClientImpl 添加拦截逻辑
public class LoggingHttpClient implements com.feign.framework.client.HttpClient {
    private final HttpClientImpl delegate;

    public LoggingHttpClient(HttpClientConfig config) {
        this.delegate = new HttpClientImpl(config);
    }

    @Override
    public Response execute(Request request) throws Exception {
        System.out.println("[请求] " + request.getMethod() + " " + request.getUrl());
        long start = System.currentTimeMillis();

        Response response = delegate.execute(request);

        long elapsed = System.currentTimeMillis() - start;
        System.out.println("[响应] " + response.getStatusCode() + " (" + elapsed + "ms)");
        return response;
    }

    // ... 其他方法委托给 delegate
}
```

---

## 项目结构

```
feign-framework/
├── core/                     # 核心抽象层 (Java)
│   ├── annotations/          # @FeignClient, @FeignMethod
│   ├── http/                 # Request, Response, HttpMethod
│   ├── client/               # HttpClient 接口
│   ├── loadbalancer/         # LoadBalancer 接口, LoadBalancerType
│   ├── retry/                # RetryPolicy 接口
│   ├── config/               # FeignConfig
│   └── FeignException.java
│
├── java-impl/                # Java 实现层
│   ├── client/               # HttpClientImpl, FeignClientFactory
│   ├── loadbalancer/         # RoundRobin, Random
│   └── retry/                # DefaultRetryPolicy
│
├── java-processor/           # 注解处理器
│   └── processor/            # FeignClientProxy, FeignClientFactory
│
├── python-impl/              # Python 实现层
│   ├── src/feign/
│   │   ├── client/           # HttpClient, FeignClient
│   │   ├── loadbalancer/     # RoundRobin, Random
│   │   ├── retry/            # DefaultRetryPolicy
│   │   ├── models.py         # Request, Response
│   │   └── interfaces.py     # ABC 接口定义
│   └── examples/
│
└── examples/                 # Java 示例
    ├── UserService.java
    └── FeignClientDemo.java
```

---

## 常见问题

### Q: 如何处理路径参数？

**Java:**
```java
@FeignMethod(method = HttpMethod.GET, path = {"users", "{id}", "posts", "{postId}"})
String getUserPost(@Path("id") Long id, @Path("postId") Long postId);
```

**Python:**
```python
def get_user_post(self, user_id: int, post_id: int) -> dict:
    # URL: {base_url}/users/{user_id}/posts/{post_id}
    pass
```

### Q: 如何设置自定义请求头？

**Java:**
```java
@FeignMethod(method = HttpMethod.GET, path = {"users"}, headers = {"Authorization: Bearer xxx", "X-Custom: value"})
String getUsers();
```

**Python:**
```python
super().__init__(
    name="service",
    url="http://localhost:8080",
    headers={"Authorization": "Bearer xxx"}
)
```

### Q: 如何启用异步调用？

**Java:** 使用 `CompletableFuture`
```java
CompletableFuture<Response> future = client.executeAsync(request);
future.thenAccept(response -> {
    System.out.println(response.getBodyAsString());
});
```

**Python:** 使用 `async/await`
```python
response = await service.create_user(data)
```

---

## API 参考

### 核心类对照表

| 功能 | Java | Python |
|------|------|--------|
| HTTP 客户端接口 | `com.feign.framework.client.HttpClient` | `feign.interfaces.HttpClient` |
| HTTP 客户端实现 | `HttpClientImpl` | `HttpClientImpl` |
| 请求模型 | `com.feign.framework.http.Request` | `feign.models.Request` |
| 响应模型 | `com.feign.framework.Response` | `feign.models.Response` |
| 负载均衡接口 | `com.feign.framework.loadbalancer.LoadBalancer` | `feign.interfaces.LoadBalancer` |
| 轮询均衡器 | `RoundRobinLoadBalancer` | `RoundRobinLoadBalancer` |
| 随机均衡器 | `RandomLoadBalancer` | `RandomLoadBalancer` |
| 重试策略 | `com.feign.framework.retry.RetryPolicy` | `feign.interfaces.RetryPolicy` |
| 默认重试 | `DefaultRetryPolicy` | `DefaultRetryPolicy` |
| 异常类 | `FeignException` | — |
