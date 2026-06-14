# Feign Framework 使用指南

类 OpenFeign 的声明式 HTTP/gRPC/WebSocket 客户端框架。

---

## 目录

- [快速开始](#快速开始)
- [注解参考](#注解参考)
- [编解码](#编解码)
- [拦截器](#拦截器)
- [负载均衡](#负载均衡)
- [重试 & 熔断](#重试--熔断)
- [降级 Fallback](#降级-fallback)
- [服务发现](#服务发现)
- [协议支持](#协议支持)
- [链路追踪](#链路追踪)
- [Spring Boot](#spring-boot)
- [完整构建链](#完整构建链)

---

## 快速开始

### Maven

```xml
<dependency><groupId>com.feign</groupId><artifactId>feign-framework-core</artifactId><version>1.0.0-SNAPSHOT</version></dependency>
<dependency><groupId>com.feign</groupId><artifactId>feign-framework-java</artifactId><version>1.0.0-SNAPSHOT</version></dependency>
<dependency><groupId>com.feign</groupId><artifactId>feign-framework-processor</artifactId><version>1.0.0-SNAPSHOT</version></dependency>
<!-- 可选: Jackson -->
<dependency><groupId>com.fasterxml.jackson.core</groupId><artifactId>jackson-databind</artifactId><version>2.16.0</version></dependency>
```

### 定义 + 调用

```java
@FeignClient(name = "user-service", url = "http://localhost:8080/api")
public interface UserService {
    @FeignMethod(method = HttpMethod.GET, path = {"users", "{id}"})
    User getUser(@Path("id") Long id);

    @FeignMethod(method = HttpMethod.POST, path = {"users"})
    User createUser(@Body User user);
}

// 创建
UserService svc = FeignClientFactory.create(UserService.class);
User user = svc.getUser(1L);
```

---

## 注解参考

### @FeignClient

| 参数 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `name` | String | `""` | 服务名 |
| `url` | String | `""` | 服务地址 |
| `loadBalancer` | LoadBalancerType | `ROUND_ROBIN` | ROUND_ROBIN / RANDOM / LEAST_CONNECTIONS |
| `connectTimeout` | int | `5000` | 连接超时 ms |
| `readTimeout` | int | `10000` | 读取超时 ms |
| `maxRetries` | int | `3` | 最大重试次数 |
| `retryInterval` | long | `1000` | 重试间隔 ms |
| `fallback` | Class | `Void.class` | 降级实现类 |

### @FeignMethod

| 参数 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `method` | HttpMethod | `GET` | GET/POST/PUT/DELETE/PATCH |
| `path` | String[] | `{}` | 路径片段，用 `/` 拼接 |
| `headers` | String[] | `{}` | 静态头 `"Key: Value"` |
| `contentType` | String | `application/json` | `application/json` / `application/x-www-form-urlencoded` |
| `name` | String | `""` | 别名 |

### @Path / @Query / @Body / @Header

```java
// @Path — 路径变量
@FeignMethod(path = {"users", "{id}", "posts", "{postId}"})
Post getPost(@Path("id") Long uid, @Path("postId") Long pid);
// → GET /users/1/posts/42

// @Query — 查询参数（自动 URL 编码）
@FeignMethod(path = {"users"})
List<User> list(@Query("page") int page, @Query("q") String keyword);
// → GET /users?page=1&q=hello+world

// @Body — 显式标记请求体（也可隐式：第一个非 @Path/@Query 对象参数）
@FeignMethod(method = HttpMethod.POST, path = {"users"})
User create(@Body User user, @Header("Authorization") String token);

// @Body(raw=true) — 跳过 Encoder，原样发送
@FeignMethod(method = HttpMethod.POST, path = {"users"})
User create(@Body(raw = true) String rawJson);

// 表单请求
@FeignMethod(method = HttpMethod.POST, path = {"login"},
             contentType = "application/x-www-form-urlencoded")
Result login(@Query("username") String u, @Query("password") String p);
```

---

## 编解码

### 默认 Gson

自动序列化/反序列化。Encoder 将 Java 对象编码为请求体，Decoder 将响应体解码为 Java 对象。

### Jackson（可选）

```java
new FeignClientFactory()
    .encoder(new JacksonEncoder())
    .decoder(new JacksonDecoder())
    .build(UserService.class);
```

### FeignResponse — 获取响应头

```java
@FeignMethod(method = HttpMethod.GET, path = {"users", "{id}"})
FeignResponse<User> getUser(@Path("id") Long id);

FeignResponse<User> resp = svc.getUser(1L);
User user = resp.getBody();
String contentType = resp.getHeader("Content-Type");
```

---

## 拦截器

```java
class AuthInterceptor implements FeignInterceptor {
    @Override public int order() { return 0; }
    @Override public Request beforeExecute(Request r) {
        r.getHeaders().put("Authorization", "Bearer xxx");
        return r;
    }
}

class LogInterceptor implements FeignInterceptor {
    @Override public int order() { return 10; }
    @Override public Request beforeExecute(Request r) { log(r); return r; }
    @Override public Response afterExecute(Response r) { log(r); return r; }
    @Override public void onError(Request r, FeignException e) { err(e); }
}

// 注册 — 自动按 order() 排序
new FeignClientFactory()
    .addInterceptor(new AuthInterceptor())
    .addInterceptor(new LogInterceptor())
    .build(UserService.class);

// 执行顺序：beforeExecute Auth(0)→Log(10) / afterExecute Log(10)→Auth(0)
```

---

## 负载均衡

```java
// 内置
@FeignClient(loadBalancer = LoadBalancerType.ROUND_ROBIN)     // 轮询
@FeignClient(loadBalancer = LoadBalancerType.RANDOM)           // 随机
@FeignClient(loadBalancer = LoadBalancerType.LEAST_CONNECTIONS) // 最少连接数

// 自定义
new FeignClientFactory()
    .loadBalancer(new MyWeightedLB())
    .build(UserService.class);
```

---

## 重试 & 熔断

### 重试

```java
@FeignClient(maxRetries = 3, retryInterval = 1000)
```
每次失败等 1s，重试时 LB 选另一台。

### 熔断器

```
CLOSED ──(failures ≥ N in window)──→ OPEN ──(cooldown)──→ HALF_OPEN
                                            probe success → CLOSED
                                            probe fail    → OPEN
```

```java
new FeignClientFactory()
    .circuitBreaker(new DefaultCircuitBreaker(5, 60_000, 30_000, 2))
    //             失败阈值 窗口ms  冷却ms  探测成功次数
    .build(UserService.class);
```

熔断 OPEN 时直接走 fallback，不发起网络调用。

---

## 降级 Fallback

```java
@FeignClient(name = "user-service", fallback = UserFallback.class)
public interface UserService { ... }

public class UserFallback implements UserService {
    public User getUser(Long id) { return new User(id, "降级数据"); }
}
```

触发：熔断 OPEN / 所有重试均失败。

---

## 服务发现

```java
@FeignClient(name = "user-service")  // 不写 url，由注册中心提供

ServiceDiscovery sd = new NacosDiscovery("nacos://localhost:8848");
new FeignClientFactory().serviceDiscovery(sd).build(UserService.class);
```

URL 解析链：`@FeignClient.url → ServiceDiscovery → LoadBalancer`

---

## 协议支持

只改 URL scheme，注解不变：

```java
@FeignClient(url = "http://host:8080")       // HTTP
@FeignClient(url = "grpc://host:50051")      // gRPC  (path = {service, method})
@FeignClient(url = "ws://host:8080/chat")    // WebSocket
```

### HTTP 连接池

```java
new HttpProtocolHandler(5000, 10000, 200, 20);
//                     connect read maxTotal maxPerRoute
```

### gRPC

```java
@FeignClient(url = "grpc://localhost:50051")
public interface UserRpc {
    @FeignMethod(path = {"UserService", "GetUser"})
    Map<String,Object> getUser(@Path("id") String id);
}
```

### WebSocket

```java
@FeignClient(url = "ws://localhost:8080/chat")
public interface ChatService {
    @FeignMethod(path = {"send"})
    void sendMessage(String msg);
}
```

---

## 链路追踪

```java
// 纯 API
Tracer tracer = Tracer.builder()
    .serviceName("order-service")
    .sampleRate(0.1)                     // 10% 采样
    .reporter(Tracer.loggingReporter())
    .build();

new FeignClientFactory()
    .addInterceptor(new TracingInterceptor(tracer))
    .build(UserService.class);
```

**链路传播：**

```
调用方                                  被调用方
  beforeExecute:
    inject X-Trace-Id: abc123
    inject X-Span-Id:  def456
  HTTP request ──────────────────────→ extract TraceContext
                                        tracer.newChildSpan()
  ←─ HTTP response
  afterExecute: span.finish() → report
```

---

## Spring Boot

```java
@SpringBootApplication
@EnableFeignClients(basePackages = "com.example.api")
public class App { ... }

@Autowired private UserService userService;
```

### application.yml

```yaml
feign:
  client:
    config:
      default:
        url: http://localhost:8080
        connect-timeout: 5000
        read-timeout: 10000
        max-retries: 3
        retry-interval: 1000
        load-balancer: ROUND_ROBIN
        encoder: jackson          # gson | jackson
        decoder: jackson
        interceptors:             # Spring bean 名
          - loggingInterceptor
          - authInterceptor
        circuit-breaker-enabled: true
        connection-pool:
          max-total: 200
          max-per-route: 20
        tracing:
          enabled: true
          service-name: my-app
          sample-rate: 0.1
          reporter: logging
          log-enabled: true
```

---

## 完整构建链

```java
UserService service = new FeignClientFactory()
    .decoder(new JacksonDecoder())
    .encoder(new JacksonEncoder())
    .protocolHandler(new HttpProtocolHandler(5000, 10000, 200, 20))
    .loadBalancer(new RoundRobinLoadBalancer())
    .circuitBreaker(new DefaultCircuitBreaker())
    .serviceDiscovery(new MyDiscovery())
    .addInterceptor(new TracingInterceptor(tracer))
    .addInterceptor(new AuthInterceptor())
    .addInterceptor(new LogInterceptor())
    .fallbackInstance(new UserFallback())
    .build(UserService.class);
```

---

## 项目结构

```
feign-framework/
├── core/                注解 接口 模型 协议 熔断 链路
├── java-impl/           HTTP/gRPC/WS 编解码 LB 重试 链路
├── java-processor/      代理 工厂 执行管道
├── feign-spring/        Spring Boot Starter
├── python-impl/         Python 实现
├── examples/            完整示例
└── docs/                文档
```

## API 对照

| 功能 | 接口 | 默认实现 |
|------|------|---------|
| 编解码 | `Encoder` `Decoder` | `GsonEncoder` `GsonDecoder` `Jackson*` |
| 协议 | `ProtocolHandler` | `Http` `Grpc` `WebSocket` |
| 拦截器 | `FeignInterceptor` | — |
| 负载均衡 | `LoadBalancer` | `RoundRobin` `Random` `LeastConnections` |
| 重试 | `RetryPolicy` | `DefaultRetryPolicy` |
| 熔断 | `CircuitBreaker` | `DefaultCircuitBreaker` (三态) |
| 服务发现 | `ServiceDiscovery` | — |
| 链路追踪 | `Tracer` `TracingInterceptor` | 内置 Logging/Zipkin 扩展点 |
