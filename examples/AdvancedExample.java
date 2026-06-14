package examples;

import com.feign.framework.*;
import com.feign.framework.annotations.*;
import com.feign.framework.circuit.*;
import com.feign.framework.codec.*;
import com.feign.framework.discovery.ServiceDiscovery;
import com.feign.framework.http.*;
import com.feign.framework.interceptor.FeignInterceptor;
import com.feign.framework.loadbalancer.*;
import com.feign.framework.protocol.*;
import com.feign.framework.trace.*;
import com.feign.processor.FeignClientFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 完整功能演示：@Path @Query @Body @Header / Jackson编解码 / 拦截器排序
 * / 负载均衡 / 熔断器 / 降级 / 服务发现 / 链路追踪 / 异步 / 响应头提取
 */
public class AdvancedExample {

    static class User {
        public Long id; public String name; public String email;
        public User() {}
        public User(Long id, String name) { this.id = id; this.name = name; }
        @Override public String toString() { return "User{id=" + id + ", name=" + name + "}"; }
    }

    // ── 客户端接口 ──
    @FeignClient(name = "user-service", url = "http://localhost:8080/api",
                 loadBalancer = LoadBalancerType.ROUND_ROBIN,
                 connectTimeout = 5000, readTimeout = 10000,
                 maxRetries = 3, retryInterval = 1000,
                 fallback = UserServiceFallback.class)
    public interface UserService {

        @FeignMethod(method = HttpMethod.GET, path = {"users", "{id}"})
        User getUser(@Path("id") Long id);

        @FeignMethod(method = HttpMethod.GET, path = {"users", "{id}"})
        FeignResponse<User> getUserWithHeaders(@Path("id") Long id);

        @FeignMethod(method = HttpMethod.GET, path = {"users"})
        List<User> listUsers(@Query("page") int page, @Query("size") int size);

        @FeignMethod(method = HttpMethod.POST, path = {"users"})
        User createUser(@Body User user, @Header("Authorization") String token);

        @FeignMethod(method = HttpMethod.POST, path = {"login"},
                     contentType = "application/x-www-form-urlencoded")
        Map<String,Object> login(@Query("username") String u, @Query("password") String p);

        @FeignMethod(method = HttpMethod.GET, path = {"users", "{id}"})
        CompletableFuture<User> getUserAsync(@Path("id") Long id);
    }

    // ── 降级 ──
    public static class UserServiceFallback implements UserService {
        @Override public User getUser(Long id) { return new User(id, "fallback"); }
        @Override public FeignResponse<User> getUserWithHeaders(Long id) { return new FeignResponse<>(new User(id, "fb"), Map.of("X-Source", "fallback")); }
        @Override public List<User> listUsers(int page, int size) { return List.of(); }
        @Override public User createUser(User u, String token) { return new User(0L, "fb"); }
        @Override public Map<String,Object> login(String u, String p) { return Map.of("status", "fallback"); }
        @Override public CompletableFuture<User> getUserAsync(Long id) { return CompletableFuture.completedFuture(new User(id, "fb")); }
    }

    // ── 拦截器 ──
    static class AuthInterceptor implements FeignInterceptor {
        @Override public int order() { return 0; }
        @Override public Request beforeExecute(Request r) { r.getHeaders().put("Authorization", "Bearer token"); return r; }
    }
    static class LogInterceptor implements FeignInterceptor {
        @Override public int order() { return 10; }
        @Override public Request beforeExecute(Request r) { System.out.println("[REQ] " + r.getMethod() + " " + r.getUrl()); return r; }
        @Override public Response afterExecute(Response r) { System.out.println("[RES] " + r.getStatusCode()); return r; }
        @Override public void onError(Request r, FeignException e) { System.err.println("[ERR] " + e.getMessage()); }
    }

    // ── Main ──
    public static void main(String[] args) {
        // 链路追踪
        Tracer tracer = Tracer.builder()
            .serviceName("demo-app")
            .sampleRate(1.0)
            .reporter(Tracer.loggingReporter())
            .build();

        UserService svc = new FeignClientFactory()
            .encoder(new JacksonEncoder())       // Jackson 编解码
            .decoder(new JacksonDecoder())
            .protocolHandler(new HttpProtocolHandler(5000, 10000, 200, 20))
            .loadBalancer(new RoundRobinLoadBalancer())
            .circuitBreaker(new DefaultCircuitBreaker(5, 60_000, 30_000, 2))
            .addInterceptor(new TracingInterceptor(tracer))  // 链路追踪
            .addInterceptor(new AuthInterceptor())
            .addInterceptor(new LogInterceptor())
            .build(UserService.class);

        System.out.println("Proxy: " + svc.getClass().getName());

        // 1. 基本调用（带 @Path）
        try { System.out.println("getUser: " + svc.getUser(1L)); }
        catch (Exception e) { System.out.println("(no server)"); }

        // 2. 响应头提取
        try {
            FeignResponse<User> resp = svc.getUserWithHeaders(1L);
            System.out.println("Headers: " + resp.getHeaders());
        } catch (Exception e) { System.out.println("(no server)"); }

        // 3. @Query 参数
        try { svc.listUsers(1, 10); } catch (Exception e) { }

        // 4. @Body + @Header
        try { svc.createUser(new User(null, "张三"), "Bearer my-token"); } catch (Exception e) { }

        // 5. 表单
        try { svc.login("admin", "123456"); } catch (Exception e) { }

        // 6. 熔断器演示
        System.out.println("\n触发熔断...");
        for (int i = 0; i < 10; i++) {
            try { svc.getUser(1L); } catch (Exception ignored) {}
        }
        // 第6+次 → 熔断 OPEN → 走 fallback
        System.out.println("Fallback: " + svc.getUser(1L)); // User{id=1, name=fallback}

        // 7. 异步
        svc.getUserAsync(2L).thenAccept(u -> System.out.println("Async: " + u));
    }
}
