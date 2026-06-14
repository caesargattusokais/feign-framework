# Feign Framework 使用指南

一个类 OpenFeign 的声明式 HTTP 客户端框架，支持 HTTP / gRPC / WebSocket。

---

## 快速开始

### 1. 依赖

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
</dependency>
```

### 2. 定义接口

```java
@FeignClient(name = "user-service", url = "http://localhost:8080/api")
public interface UserService {

    @FeignMethod(method = HttpMethod.GET, path = {"users", "{id}"})
    User getUser(@Path("id") Long id);

    @FeignMethod(method = HttpMethod.POST, path = {"users"})
    User createUser(User user);  // Encoder 自动序列化
}
```

### 3. 调用

```java
// 纯 API
UserService svc = FeignClientFactory.create(UserService.class);
User user = svc.getUser(1L);

// Spring Boot
@Autowired private UserService userService;
```

---

## 注解参考

### @FeignClient

| 参数 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `name` | String | `""` | 服务名 |
| `url` | String | `""` | 服务地址 |
| `loadBalancer` | LoadBalancerType | `ROUND_ROBIN` | 内置策略或自定义 |
| `connectTimeout` | int | `5000` | 连接超时 ms |
| `readTimeout` | int | `10000` | 读取超时 ms |
| `maxRetries` | int | `3` | 最大重试次数 |
| `retryInterval` | long | `1000` | 重试间隔 ms |
| `fallback` | Class | `Void.class` | 降级实现类 |

### @FeignMethod

| 参数 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `method` | HttpMethod | `GET` | HTTP 方法 |
| `path` | String[] | `{}` | 路径片段 |
| `headers` | String[] | `{}` | 请求头 `"Key: Value"` |
| `contentType` | String | `application/json` | Content-Type |
| `name` | String | `""` | 方法别名 |

### @Path — 路径参数

```java
@FeignMethod(path = {"users", "{id}", "posts", "{postId}"})
User getPost(@Path("id") Long uid, @Path("postId") Long pid);
// → GET /users/1/posts/42
```

### @Query — 查询参数

```java
@FeignMethod(path = {"users"})
List<User> list(@Query("page") int page, @Query("size") int size);
// → GET /users?page=1&size=10
```

---

## 编解码（Encoder / Decoder）

### 默认：Gson

```java
// Encoder: User → JSON bytes
User createUser(User user);

// Decoder: JSON bytes → User
User getUser(@Path("id") Long id);

// 获取响应头
FeignResponse<User> resp = svc.getUser(1L);
resp.getBody();           // User 对象
resp.getHeader("Location"); // 响应头
```

### 自定义编解码

```java
new FeignClientFactory()
    .encoder(new JacksonEncoder())
    .decoder(new JacksonDecoder())
    .build(UserService.class);
```

---

## 表单请求

```java
@FeignMethod(method = HttpMethod.POST, path = {"login"},
             contentType = "application/x-www-form-urlencoded")
Map<String, Object> login(@Query("username") String u, @Query("password") String p);
// → POST /login  Content-Type: form-urlencoded  body: username=admin&password=123
```

---

## 拦截器链

```java
class AuthInterceptor implements FeignInterceptor {
    @Override public int order() { return 0; }   // 先执行
    @Override public Request beforeExecute(Request r) {
        r.getHeaders().put("Authorization", "Bearer xxx");
        return r;
    }
}

class LogInterceptor implements FeignInterceptor {
    @Override public int order() { return 10; }  // 后执行
    @Override public Request beforeExecute(Request r) { ... }
    @Override public Response afterExecute(Response r) { ... }
    @Override public void onError(Request r, FeignException e) { ... }
}

// 注册 — 自动按 order() 排序
new FeignClientFactory()
    .addInterceptor(new AuthInterceptor())
    .addInterceptor(new LogInterceptor())
    .build(UserService.class);

// beforeExecute: Auth(0) → Log(10)
// afterExecute:  Log(10) → Auth(0) (逆序)
```

---

## 负载均衡

```java
// 内置策略
@FeignClient(loadBalancer = LoadBalancerType.ROUND_ROBIN) // 轮询
@FeignClient(loadBalancer = LoadBalancerType.RANDOM)       // 随机
@FeignClient(loadBalancer = LoadBalancerType.LEAST_CONNECTIONS) // 最少连接数

// 自定义
public class WeightedLB implements LoadBalancer {
    public String select(Request req, List<String> servers) { ... }
    // ... 其他方法
}

new FeignClientFactory()
    .loadBalancer(new WeightedLB())
    .build(UserService.class);
```

---

## 重试机制

```java
@FeignClient(maxRetries = 3, retryInterval = 1000)
```

每次失败后等 `retryInterval` ms，重试时 LB 会选另一台服务器。

---

## 熔断器（Circuit Breaker）

```
CLOSED ──(failures ≥ threshold)──→ OPEN ──(cooldown)──→ HALF_OPEN ──(successes)──→ CLOSED
                                                            │
                                                            └──(any failure)──→ OPEN
```

```java
// 5 failures in 60s → OPEN, 30s cooldown → probe, 2 successes → CLOSED
new FeignClientFactory()
    .circuitBreaker(new DefaultCircuitBreaker())
    .build(UserService.class);

// 自定义阈值
new DefaultCircuitBreaker(10, 120_000, 60_000, 3);
//                         ↑   ↑        ↑       ↑
//                  10 failures 2min窗口 1min冷却 3次成功恢复
```

熔断 OPEN 时所有请求直接走 fallback，不发起网络调用。

---

## Fallback（降级）

```java
@FeignClient(name = "user-service", fallback = UserFallback.class)

public class UserFallback implements UserService {
    public User getUser(Long id) { return new User(id, "降级"); }
}
```

触发条件（任一）：
- 熔断器 OPEN
- 所有重试均失败

---

## 服务发现

```java
// 不需要 url，由注册中心提供
@FeignClient(name = "user-service")
public interface UserService { ... }

// 对接 Nacos / Consul / Eureka
ServiceDiscovery discovery = new NacosDiscovery("nacos://localhost:8848");

new FeignClientFactory()
    .serviceDiscovery(discovery)
    .build(UserService.class);
```

URL 解析流程：`@FeignClient.url → ServiceDiscovery → LoadBalancer`

---

## 协议支持

同一个注解，改 URL scheme 即可切换协议：

```java
@FeignClient(url = "http://host:8080")    // HTTP (默认)
@FeignClient(url = "grpc://host:50051")   // gRPC
@FeignClient(url = "ws://host:8080/chat") // WebSocket
```

gRPC 的 `path` 为 `{serviceName}/{methodName}`：

```java
@FeignClient(url = "grpc://localhost:50051")
public interface UserRpc {
    @FeignMethod(path = {"UserService", "GetUser"})
    Map<String, Object> getUser(@Path("id") String id);
}
```

---

## HTTP 连接池

```java
// 默认：maxTotal=200, maxPerRoute=20
new HttpProtocolHandler(5000, 10000);

// 自定义
new HttpProtocolHandler(5000, 10000, 500, 50);
//                                ↑    ↑
//                         maxTotal  maxPerRoute
```

---

## 异步调用

```java
@FeignMethod(method = HttpMethod.GET, path = {"users", "{id}"})
CompletableFuture<User> getUserAsync(@Path("id") Long id);

CompletableFuture<User> future = svc.getUserAsync(1L);
future.thenAccept(user -> System.out.println(user));
```

---

## Spring Boot 集成

```java
@SpringBootApplication
@EnableFeignClients(basePackages = "com.example.api")
public class App { ... }

// application.yml
feign:
  client:
    config:
      default:
        connect-timeout: 5000
        max-retries: 3
        load-balancer: ROUND_ROBIN
        interceptors: [loggingInterceptor, authInterceptor]
        circuit-breaker-enabled: true
        connection-pool:
          max-total: 200
          max-per-route: 20

// 直接注入
@RestController
public class UserController {
    @Autowired private UserService userService;
}
```

---

## 完整构建链

```java
UserService service = new FeignClientFactory()
    .decoder(new JacksonDecoder())
    .encoder(new JacksonEncoder())
    .protocolHandler(new HttpProtocolHandler(5000, 10000, 200, 20))
    .loadBalancer(new MyLoadBalancer())
    .circuitBreaker(new DefaultCircuitBreaker())
    .serviceDiscovery(new MyDiscovery())
    .addInterceptor(new AuthInterceptor())
    .addInterceptor(new LogInterceptor())
    .build(UserService.class);
```

---

## 项目结构

```
feign-framework/
├── core/                   注解 接口 模型 协议抽象
├── java-impl/              HTTP/gRPC/WS实现 编解码 LB 重试 熔断
├── java-processor/         动态代理 执行管道 工厂
├── feign-spring/           Spring Boot Starter @EnableFeignClients
├── python-impl/            Python 实现
├── examples/               完整示例
└── docs/                   文档
```

## API 对照表

| 功能 | 接口/抽象 | 默认实现 |
|------|----------|---------|
| 编解码器 | `Encoder` `Decoder` | `GsonEncoder` `GsonDecoder` |
| 协议处理器 | `ProtocolHandler` | `HttpProtocolHandler` `GrpcProtocolHandler` `WebSocketProtocolHandler` |
| 拦截器 | `FeignInterceptor` | — |
| 负载均衡 | `LoadBalancer` | `RoundRobin` `Random` `LeastConnections` |
| 重试策略 | `RetryPolicy` | `DefaultRetryPolicy` |
| 熔断器 | `CircuitBreaker` | `DefaultCircuitBreaker` (三态) |
| 服务发现 | `ServiceDiscovery` | — |
