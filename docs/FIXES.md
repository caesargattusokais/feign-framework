# Java 实现层接口不匹配问题修复方案

## 🚨 问题总结

### 1. LoadBalancer 接口已修复 ✅
**文件：** `java-impl/src/main/java/com/feign/framework/loadbalancer/RoundRobinLoadBalancer.java`
**文件：** `java-impl/src/main/java/com/feign/framework/loadbalancer/RandomLoadBalancer.java`

✅ 已修改 `select()` 方法签名以匹配接口：
```java
// 之前（错误）
String select(String serviceName);

// 之后（正确）
String select(Request request, List<String> servers);
```

✅ 已添加缺失的方法：
```java
LoadBalancerType getType();
```

---

### 2. RetryPolicy 接口需要修复 ❌

**文件：** `java-impl/src/main/java/com/feign/framework/retry/DefaultRetryPolicy.java`

**需要修改的方法：**

#### 方法 1: `canRetry(int retryCount, Throwable lastException)` ❌
```java
// 之前（错误）
boolean canRetry(int retryCount, Throwable lastException);

// 之后（正确）
boolean canRetry(Exception e, int retryCount);
```

**完整修复后：**
```java
@Override
public boolean canRetry(Exception e, int retryCount) {
    if (!enabled) {
        return false;
    }

    if (retryCount >= maxRetries) {
        return false;
    }

    if (e instanceof RuntimeException) {
        return true;
    }

    if (e instanceof IOException) {
        return true;
    }

    if (e instanceof com.feign.framework.FeignException) {
        return true;
    }

    return false;
}
```

#### 方法 2: `maxRetries()` ❌
```java
// 之前（错误）
int maxRetries() {
    return maxRetries;
}

// 之后（正确）
int getMaxRetries() {
    return maxRetries;
}
```

#### 方法 3: `retryInterval()` ❌
```java
// 之前（错误）
long retryInterval() {
    return retryInterval;
}

// 之后（正确）
long getRetryInterval() {
    return retryInterval;
}
```

#### 方法 4: 缺少 `getLoadBalancerType()` ❌
```java
// 添加新方法
@Override
public String getLoadBalancerType() {
    return loadBalancerType;
}
```

#### 方法 5: 缺少 `isEnabled()` ❌
```java
// 添加新方法
@Override
public boolean isEnabled() {
    return enabled;
}
```

#### 添加属性（在类开头）
```java
private boolean enabled = true;
private String loadBalancerType = "ROUND_ROBIN";
```

#### 添加 setter 方法
```java
public void setEnabled(boolean enabled) {
    this.enabled = enabled;
}

public void setLoadBalancerType(String loadBalancerType) {
    this.loadBalancerType = loadBalancerType;
}
```

---

### 3. HttpClient 接口需要修复 ❌

**文件：** `java-impl/src/main/java/com/feign/framework/client/HttpClientImpl.java`

**需要修改的方法：**

#### 方法 1: `execute(Request request, Class<T> responseType)` ❌
```java
// 之前（错误）
<T> T execute(Request request, Class<T> responseType) throws FeignException {
    try {
        CloseableHttpResponse httpResponse = executeHttpRequest(request);
        Response response = parseResponse(httpResponse);
        return serializeResponse(response, responseType);
    } catch (IOException e) {
        throw new FeignException("Request failed", e);
    }
}

// 之后（正确）
Response execute(Request request) throws FeignException {
    try {
        CloseableHttpResponse httpResponse = executeHttpRequest(request);
        Response response = parseResponse(httpResponse);
        return response;
    } catch (IOException e) {
        throw new FeignException("Request failed", e);
    }
}
```

#### 方法 2: `executeAsync(Request request, Class<T> responseType)` ❌
```java
// 之前（错误）
<T> CompletableFuture<T> executeAsync(Request request, Class<T> responseType) {
    return CompletableFuture.supplyAsync(() -> {
        try {
            CloseableHttpResponse httpResponse = executeHttpRequest(request);
            Response response = parseResponse(httpResponse);
            return serializeResponse(response, responseType);
        } catch (IOException e) {
            throw new CompletionException(new FeignException("Async request failed", e));
        }
    });
}

// 之后（正确）
CompletableFuture<Response> executeAsync(Request request) {
    return CompletableFuture.supplyAsync(() -> {
        try {
            CloseableHttpResponse httpResponse = executeHttpRequest(request);
            Response response = parseResponse(httpResponse);
            return response;
        } catch (IOException e) {
            throw new CompletionException(new FeignException("Async request failed", e));
        }
    });
}
```

#### 方法 3: `isAvailable(String url)` ❌
```java
// 之前（错误）
boolean isAvailable(String url) {
    return true;
}

// 之后（正确）
boolean isAvailable() {
    return true;
}
```

#### 方法 4: 缺少 `getName()` ❌
```java
// 添加新方法
@Override
public String getName() {
    return "HttpClientImpl";
}
```

#### 方法 5: 删除 `serializeResponse()` 方法 ❌
这个方法是用于泛型版本的，不再需要。删除整个 `serializeResponse()` 方法。

#### 方法 6: 修改 `parseResponse()` ❌
```java
// 之前（错误）
private Response parseResponse(CloseableHttpResponse httpResponse) throws IOException {
    int statusCode = httpResponse.getCode();
    Map<String, String> headers = new HashMap<>();
    for (org.apache.hc.core5.http.Header header : httpResponse.getHeaders()) {
        headers.put(header.getName(), header.getValue());
    }

    HttpEntity entity = httpResponse.getEntity();
    String body = entity != null ? EntityUtils.toString(entity) : null;

    return new Response(statusCode, headers, body);  // Response 接口没有这个构造函数！
}

// 之后（正确）
private Response parseResponse(String url, CloseableHttpResponse httpResponse) throws IOException {
    int statusCode = httpResponse.getCode();
    Map<String, String> headers = new HashMap<>();
    for (org.apache.hc.core5.http.Header header : httpResponse.getHeaders()) {
        headers.put(header.getName(), header.getValue());
    }

    HttpEntity entity = httpResponse.getEntity();
    String body = entity != null ? EntityUtils.toString(entity) : null;

    // 使用 Response.of() 静态工厂方法
    return Response.of(statusCode, headers, body);
}
```

#### 修改 execute() 和 executeAsync() 方法调用 parseResponse() 时传入 url 参数
```java
// execute() 方法
Response response = parseResponse(request.getUrl(), httpResponse);

// executeAsync() 方法
Response response = parseResponse(request.getUrl(), httpResponse);
```

---

### 4. Response 接口需要增强 ❌

**文件：** `core/src/main/java/com/feign/framework/Response.java`

**需要添加：**

#### 静态工厂方法
```java
/**
 * Creates a Response instance
 * @param statusCode the HTTP status code
 * @param headers the response headers
 * @param body the response body as string
 * @return a new Response instance
 */
static Response of(int statusCode, Map<String, String> headers, String body) {
    return new Response() {
        @Override
        public int getStatusCode() {
            return statusCode;
        }

        @Override
        public String getUrl() {
            return null;
        }

        @Override
        public String getStatusText() {
            return getStatusText(statusCode);
        }

        @Override
        public Map<String, String> getHeaders() {
            return headers != null ? headers : Collections.emptyMap();
        }

        @Override
        public byte[] getBody() {
            return body != null ? body.getBytes() : new byte[0];
        }
    };
}

private static String getStatusText(int statusCode) {
    return switch (statusCode) {
        case 200 -> "OK";
        case 201 -> "Created";
        case 204 -> "No Content";
        case 400 -> "Bad Request";
        case 401 -> "Unauthorized";
        case 403 -> "Forbidden";
        case 404 -> "Not Found";
        case 500 -> "Internal Server Error";
        case 502 -> "Bad Gateway";
        case 503 -> "Service Unavailable";
        default -> "Unknown";
    };
}
```

---

## 📝 修复步骤总结

### 第 1 步：修复 DefaultRetryPolicy.java
1. 修改 `canRetry()` 方法签名
2. 方法名改为 `getMaxRetries()` 和 `getRetryInterval()`
3. 添加 `getLoadBalancerType()` 方法
4. 添加 `isEnabled()` 方法
5. 添加 `enabled` 和 `loadBalancerType` 属性
6. 添加对应的 setter 方法

### 第 2 步：修复 HttpClientImpl.java
1. 修改 `execute()` 方法 - 移除泛型参数，返回 Response
2. 修改 `executeAsync()` 方法 - 移除泛型参数，返回 CompletableFuture<Response>
3. 修改 `isAvailable()` 方法 - 移除 url 参数
4. 添加 `getName()` 方法
5. 删除 `serializeResponse()` 方法
6. 修改 `parseResponse()` 方法签名，添加 url 参数
7. 修改 `parseResponse()` 使用 `Response.of()` 静态工厂方法
8. 在 `execute()` 和 `executeAsync()` 调用 `parseResponse()` 时传入 url 参数

### 第 3 步：修复 Response.java
1. 添加 `Response.of()` 静态工厂方法
2. 添加 `getStatusText()` 私有辅助方法
3. 添加 `getUrl()` 方法（之前已经添加）

---

## ✅ 修复后的效果

修复后，所有接口和实现将完全匹配，不会再有编译错误。

**验证方法：**
```bash
# 编译 core 模块
cd D:/feign-framework/core
mvn clean compile

# 编译 java-impl 模块
cd D:/feign-framework/java-impl
mvn clean compile

# 运行测试
mvn test
```
