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
import com.feign.processor.FeignClientFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Advanced example demonstrating ALL Feign framework capabilities:
 *
 * <ul>
 *   <li>@Path / @Query parameter binding</li>
 *   <li>Typed response + FeignResponse (headers)</li>
 *   <li>Encoder (auto body serialization)</li>
 *   <li>Interceptor chain with ordering</li>
 *   <li>Custom load balancer</li>
 *   <li>Circuit breaker (3-state)</li>
 *   <li>Service discovery</li>
 *   <li>Fallback</li>
 *   <li>Async execution</li>
 *   <li>Custom protocol handler (connection pool)</li>
 * </ul>
 */
public class AdvancedExample {

    // ── Domain ──
    public static class User {
        public Long id;
        public String name;
        public String email;
        public User() {}
        public User(Long id, String name) { this.id = id; this.name = name; }
        @Override public String toString() { return "User{id=" + id + ", name=" + name + "}"; }
    }

    // ── Feign client ──
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
        User createUser(User user); // Encoder auto-serializes

        @FeignMethod(method = HttpMethod.GET, path = {"users", "{id}"})
        CompletableFuture<User> getUserAsync(@Path("id") Long id);
    }

    public static class UserServiceFallback implements UserService {
        @Override public User getUser(Long id) { return new User(id, "fallback"); }
        @Override public FeignResponse<User> getUserWithHeaders(Long id) { return new FeignResponse<>(new User(id, "fallback"), Map.of()); }
        @Override public List<User> listUsers(int page, int size) { return List.of(); }
        @Override public User createUser(User u) { return new User(0L, "fallback"); }
        @Override public CompletableFuture<User> getUserAsync(Long id) { return CompletableFuture.completedFuture(new User(id, "fallback")); }
    }

    // ── Interceptors (ordered) ──
    static class AuthInterceptor implements FeignInterceptor {
        @Override public int order() { return 0; }
        @Override public Request beforeExecute(Request r) {
            r.getHeaders().put("Authorization", "Bearer token");
            return r;
        }
    }

    static class LogInterceptor implements FeignInterceptor {
        @Override public int order() { return 10; }
        @Override public Request beforeExecute(Request r) {
            System.out.println("[REQ] " + r.getMethod() + " " + r.getUrl());
            return r;
        }
        @Override public Response afterExecute(Response r) {
            System.out.println("[RES] " + r.getStatusCode());
            return r;
        }
        @Override public void onError(Request req, FeignException e) {
            System.err.println("[ERR] " + e.getMessage());
        }
    }

    // ── Main ──
    public static void main(String[] args) {
        UserService service = new FeignClientFactory()
            .decoder(new GsonDecoder())
            .encoder(new GsonEncoder())
            .protocolHandler(new HttpProtocolHandler(5000, 10000, 200, 20))
            .loadBalancer(new RoundRobinLoadBalancer())
            .circuitBreaker(new DefaultCircuitBreaker(5, 60_000, 30_000, 2))
            .addInterceptor(new AuthInterceptor())
            .addInterceptor(new LogInterceptor())
            .build(UserService.class);

        System.out.println("Proxy: " + service.getClass().getName());

        // Sync typed call
        try { User u = service.getUser(1L); System.out.println(u); }
        catch (Exception e) { System.out.println("(no server) " + e.getMessage()); }

        // FeignResponse — body + headers
        try {
            FeignResponse<User> resp = service.getUserWithHeaders(1L);
            System.out.println("Body: " + resp.getBody());
            System.out.println("Content-Type: " + resp.getHeader("Content-Type"));
        } catch (Exception e) { System.out.println("(no server) " + e.getMessage()); }

        // Encoder serializes User → JSON automatically
        try { User created = service.createUser(new User(null, "张三")); System.out.println(created); }
        catch (Exception e) { System.out.println("(no server) " + e.getMessage()); }

        // Circuit breaker: after 5 failures, fast-fails with fallback for 30s
        for (int i = 0; i < 10; i++) {
            try { service.getUser(1L); } catch (Exception ignored) {}
        }
        // 6th+ call → circuit OPEN → fallback kicks in
        User u = service.getUser(1L);
        System.out.println("Fallback result: " + u); // User{id=1, name=fallback}

        // Async
        service.getUserAsync(2L).thenAccept(user -> System.out.println("Async: " + user));
    }
}
