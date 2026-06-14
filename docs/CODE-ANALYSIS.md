# Feign Framework 代码分析

## 项目概览

| 指标 | 数值 |
|------|------|
| Java 源文件 | 60（不含测试） |
| 总 Java 文件 | 76 |
| 总代码行数 | ~6,600 |
| Maven 模块 | 4（core / java-impl / java-processor / feign-spring） |
| Python 模块 | 1 |
| 设计模式 | 代理 / 工厂 / 策略 / 模板方法 / Builder / 拦截器链 |

---

## 一、模块架构

```
┌─────────────────────────────────────────────────────────────┐
│                    feign-spring                              │
│  @EnableFeignClients  FeignClientRegistrar  AutoConfiguration│
│  FeignProperties  FeignClientFactoryBean                     │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│                  java-processor                              │
│  FeignClientProxy  FeignClientFactory  FeignClientProcessor │
│  RequestBuilder  UrlResolver  RetryExecutor                 │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│                    java-impl                                 │
│  HttpProtocolHandler  GrpcProtocolHandler  WebSocketHandler │
│  GsonEncoder/Decoder  JacksonEncoder/Decoder                │
│  RoundRobin  Random  LeastConnections                       │
│  DefaultRetryPolicy  DefaultCircuitBreaker                  │
│  TracingInterceptor                                         │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│                       core                                   │
│  注解(@FeignClient/@FeignMethod/@Path/@Query/@Body/@Header) │
│  接口(HttpClient/LoadBalancer/RetryPolicy/ProtocolHandler)  │
│  接口(CircuitBreaker/Encoder/Decoder/ServiceDiscovery)     │
│  模型(Request/Response/FeignResponse/TraceContext/Span)     │
└─────────────────────────────────────────────────────────────┘
```

**依赖方向：全部向下依赖 core，无循环依赖。**

---

## 二、核心流程

### 2.1 请求执行管道

```
客户端调用 proxy.getUser(1L)
  │
  ▼
FeignClientProxy.invoke()
  │
  ├─ circuitBreaker.allowRequest()       ← 熔断检查
  │
  ├─ RequestBuilder.build()              ← 解析注解 + 参数
  │    ├─ resolvePath()                   @Path 替换
  │    ├─ extractQueryParams()            @Query 提取 + URLEncoder
  │    ├─ extractAllHeaders()             @FeignMethod.headers + @Header
  │    └─ encodeBody()                    @Body / 隐式 → Encoder
  │
  ├─ UrlResolver.resolve()               annotation → discovery → LB
  │
  ├─ RequestBuilder.assembleUrl()        base + path + ?params
  │
  ├─ interceptors.beforeExecute()        Auth(0) → Log(10)
  │
  ├─ RetryExecutor.execute()             
  │    └─ for each attempt:
  │         ├─ protocolHandler.execute()
  │         ├─ onSuccess → circuitBreaker.onSuccess()
  │         └─ onFailure → circuitBreaker.onFailure()
  │              └─ shouldRetry? → sleep → resolveBaseUrl(LB切换)
  │
  ├─ interceptors.afterExecute()         Log(10) → Auth(0)
  │
  └─ decode()                            Decoder + FeignResponse 包装
```

### 2.2 配置解析链

```
FeignClientFactory.build()
  │
  ├─ encoder/decoder    → GsonEncoder(默认) / Jackson(可选) / 自定义
  ├─ protocolHandler    → url scheme 自动匹配: http→HttpProtocolHandler
  │                        grpc→GrpcProtocolHandler  ws→WebSocketHandler
  ├─ loadBalancer       → @FeignClient 注解 / factory 方法
  ├─ circuitBreaker     → factory 方法 / Spring auto-config
  ├─ serviceDiscovery   → 自定义 → 对接 Nacos/Consul/Eureka
  ├─ interceptors       → 列表 → 按 order() 排序
  └─ fallbackInstance   → Spring bean / 手动 new
```

---

## 三、设计模式分析

### 3.1 代理模式 (Proxy Pattern)

```
FeignClientProxy implements InvocationHandler
    ↓
Proxy.newProxyInstance(interface, handler)
    ↓
调用 proxy.getUser(1L)
    → invoke() 拦截 → 构建 Request → 执行 HTTP → 返回解码对象
```

**为什么不用编译时代码生成？**  
运行时动态代理足够灵活，无需编译步骤。`FeignClientProcessor` 仅做编译期校验。

### 3.2 策略模式 (Strategy Pattern)

所有可插拔组件都是策略模式：

```
LoadBalancer   → RoundRobin / Random / LeastConnections / 自定义
RetryPolicy    → DefaultRetryPolicy / 自定义
ProtocolHandler→ Http / Grpc / WebSocket / 自定义
Encoder/Decoder→ Gson / Jackson / 自定义
CircuitBreaker → DefaultCircuitBreaker / 自定义
ServiceDiscovery → Nacos / Consul / Eureka / 自定义
```

用户只需实现接口，通过 `FeignClientFactory` 注入。

### 3.3 拦截器链 (Chain of Responsibility)

```java
interceptors = [Auth(0), Log(10), Trace(0)]
// 自动按 order() 排序 → [Auth(0), Trace(0), Log(10)]

beforeExecute: Auth(0) → Trace(0) → Log(10)    // 升序
afterExecute:  Log(10) → Trace(0) → Auth(0)    // 逆序
```

`TracingInterceptor`、`AuthInterceptor`、`LogInterceptor` 都是链中一环，互不感知。

### 3.4 模板方法 & Builder

- `Tracer.builder().serviceName().sampleRate().build()` — Builder 模式
- `FeignClientFactory.decoder().encoder().build()` — Fluent API + Builder
- `FeignProperties.ClientConfig.merge()` — 配置继承（default → 特定服务覆盖）

---

## 四、类职责矩阵

### 4.1 core 模块 — 15 个类

| 类 | 行数 | 职责 |
|---|------|------|
| `@FeignClient` | ~45 | 客户端配置注解 |
| `@FeignMethod` | ~20 | 方法配置注解（含 contentType） |
| `@Path` | ~15 | 路径变量绑定 |
| `@Query` | ~20 | 查询参数绑定 |
| `@Body` | ~18 | 显式请求体标记 |
| `@Header` | ~18 | 动态请求头绑定 |
| `Request` (interface) | ~120 | 请求模型 + `of()` 工厂 |
| `Response` (interface) | ~150 | 响应模型 + `of()` 工厂 |
| `FeignResponse` | ~30 | 响应体+头包装 |
| `FeignException` | ~240 | 异常体系（HTTP 状态码映射） |
| `Encoder` | ~12 | 编码器接口 |
| `Decoder` | ~35 | 解码器接口 |
| `ProtocolHandler` | ~35 | 协议处理接口 |
| `CircuitBreaker` | ~30 | 三态熔断接口 |
| `ServiceDiscovery` | ~18 | 服务发现接口 |
| `FeignInterceptor` | ~70 | 拦截器接口（含 order()） |
| `LoadBalancer` | ~45 | 负载均衡接口 |
| `RetryPolicy` | ~45 | 重试策略接口 |
| `Tracer` | ~80 | 链路追踪器（Builder） |
| `Span` | ~55 | 追踪 Span |
| `TraceContext` | ~20 | 追踪上下文 |

### 4.2 java-impl 模块 — 12 个类

| 类 | 行数 | 职责 |
|---|------|------|
| `HttpProtocolHandler` | ~195 | HTTP 客户端（连接池） |
| `GrpcProtocolHandler` | ~170 | gRPC 客户端（byte[] marshaller + 保活） |
| `WebSocketProtocolHandler` | ~240 | WebSocket 客户端（PING/PONG + 重连） |
| `GsonEncoder` | ~15 | JSON 编码（默认） |
| `GsonDecoder` | ~25 | JSON 解码（默认） |
| `JacksonEncoder` | ~25 | JSON 编码（Jackson） |
| `JacksonDecoder` | ~30 | JSON 解码（Jackson） |
| `RoundRobinLoadBalancer` | ~35 | 轮询 LB |
| `RandomLoadBalancer` | ~35 | 随机 LB |
| `LeastConnectionsLoadBalancer` | ~75 | 最少连接 LB（atomic counter） |
| `DefaultRetryPolicy` | ~65 | 重试策略（异常类型判断） |
| `DefaultCircuitBreaker` | ~130 | 三态熔断（滑动窗口 + CAS 状态转换） |
| `TracingInterceptor` | ~120 | 链路拦截（注入/提取 headers） |
| `TracingConfig` | ~60 | 链路追踪配置 |

### 4.3 java-processor 模块 — 7 个类

| 类 | 行数 | 职责 |
|---|------|------|
| `FeignClientProxy` | ~170 | 执行入口（编排） |
| `FeignClientFactory` | ~110 | 代理工厂（Fluent API + 依赖注入） |
| `RequestBuilder` | ~140 | 请求构建（路径 + 参数 + body + Header） |
| `UrlResolver` | ~55 | URL 解析（annotation → discovery → LB） |
| `RetryExecutor` | ~85 | 重试执行器（循环 + CB） |
| `FeignClientMetadata` | ~50 | 注解元数据 |
| `FeignClientProcessor` | ~45 | JSR 269 编译期校验 |

### 4.4 feign-spring 模块 — 5 个类

| 类 | 行数 | 职责 |
|---|------|------|
| `@EnableFeignClients` | ~15 | 启用注解（@Import） |
| `FeignClientRegistrar` | ~130 | 扫描 @FeignClient → 注册 FactoryBean |
| `FeignClientFactoryBean` | ~150 | FactoryBean（Spring DI） |
| `FeignAutoConfiguration` | ~25 | 自动装配（Decoder bean） |
| `FeignProperties` | ~100 | 配置属性（application.yml） |

---

## 五、关键设计决策

### 5.1 协议抽象 vs 独立客户端

**决策：** 统一 `ProtocolHandler` 接口 + URL scheme 自动路由

```
http:// → HttpProtocolHandler（Apache HttpClient 5, 连接池）
grpc:// → GrpcProtocolHandler（byte[] Marshaller, HTTP/2 保活）
ws://   → WebSocketProtocolHandler（PING/PONG, 自动重连）
```

**优势：** 用户只需改 URL，注解不变。添加新协议只实现一个接口。

### 5.2 动态代理 vs 编译期生成

**决策：** 运行时 `Proxy.newProxyInstance` + JSR 269 编译期校验

- 运行时代理：灵活，无编译步骤，开发体验好
- 编译期校验：`FeignClientProcessor` 在编译时检查注解正确性

### 5.3 熔断器状态管理

**决策：** `AtomicReference<State>` + CAS 操作

```
CLOSED → OPEN: CAS(CLOSED, OPEN)
OPEN → HALF_OPEN: CAS(OPEN, HALF_OPEN)
HALF_OPEN → CLOSED: successCounter >= threshold
HALF_OPEN → OPEN: CAS(HALF_OPEN, OPEN)
```

线程安全，无锁。HALF_OPEN 允许多个探测请求（非单探针），N 次连续成功后关闭（置信确认）。

### 5.4 请求体编码分离

**决策：** Encoder 在 Proxy 层面执行，ProtocolHandler 不关心编码

```
Proxy: User → Encoder → byte[] → Request
Handler: Request → byte[] → wire（原样传输，零拷贝）
```

gRPC 的 `Marshaller<byte[]>` 和 HTTP 的 `ByteArrayEntity` 都接受已编码字节，不二次编码。

---

## 六、扩展点

用一行代码添加新能力：

```java
// 协议
FeignClientProxy.addProtocolHandler(new MyProtocolHandler());

// 编解码
new FeignClientFactory().decoder(new MyDecoder()).encoder(new MyEncoder());

// 负载均衡
new FeignClientFactory().loadBalancer(new MyLoadBalancer());

// 熔断
new FeignClientFactory().circuitBreaker(new MyCircuitBreaker());

// 服务发现
new FeignClientFactory().serviceDiscovery(new NacosDiscovery());

// 链路追踪
Tracer tracer = Tracer.builder().reporter(new ZipkinReporter()).build();
new FeignClientFactory().addInterceptor(new TracingInterceptor(tracer));

// 自定义拦截器
new FeignClientFactory().addInterceptor(new MyInterceptor(), 5);
```

---

## 七、待优化项

| 优先级 | 项目 | 说明 |
|--------|------|------|
| 高 | 测试覆盖率 | 仅无外部依赖的单元测试，缺 HTTP/gRPC/WS 集成测试 |
| 高 | 性能基准 | 无压力测试数据 |
| 中 | 异步执行器池 | `CompletableFuture.supplyAsync()` 用 ForkJoinPool，建议可配置线程池 |
| 中 | gRPC Proto 支持 | 当前仅 JSON string，缺 Protobuf 原生支持 |
| 低 | 指标监控 | 请求计数/延迟/错误率无内置暴露（Prometheus/Micrometer） |
| 低 | Python 同步 | Python 实现滞后于 Java 功能 |
