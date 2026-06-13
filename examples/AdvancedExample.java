package examples;

import com.feign.framework.FeignException;
import com.feign.framework.Response;
import com.feign.framework.annotations.FeignClient;
import com.feign.framework.annotations.FeignMethod;
import com.feign.framework.annotations.Path;
import com.feign.framework.codec.Decoder;
import com.feign.framework.codec.GsonDecoder;
import com.feign.framework.http.HttpMethod;
import com.feign.framework.http.Request;
import com.feign.framework.interceptor.FeignInterceptor;
import com.feign.framework.loadbalancer.LoadBalancer;
import com.feign.framework.loadbalancer.LoadBalancerType;
import com.feign.framework.loadbalancer.RoundRobinLoadBalancer;
import com.feign.framework.protocol.HttpProtocolHandler;
import com.feign.processor.FeignClientFactory;
import com.google.gson.Gson;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Demonstrates all Feign framework capabilities:
 * <ul>
 *   <li>Typed response decoding (User object, not raw Response)</li>
 *   <li>Custom decoder (Gson)</li>
 *   <li>Custom load balancer</li>
 *   <li>Connection pooling (via HttpProtocolHandler)</li>
 *   <li>Interceptor ordering</li>
 *   <li>Async execution</li>
 *   <li>@Path parameter resolution</li>
 * </ul>
 */
public class AdvancedExample {

    // ── Domain model ──
    public static class User {
        public Long id;
        public String name;
        public String email;
        @Override public String toString() { return "User{id=" + id + ", name=" + name + "}"; }
    }

    // ── Feign client (returns TYPED objects, not Response!) ──
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

        /** Returns decoded User object — not raw Response */
        @FeignMethod(method = HttpMethod.GET, path = {"users", "{id}"})
        User getUser(@Path("id") Long userId);

        /** Async version */
        @FeignMethod(method = HttpMethod.GET, path = {"users", "{id}"})
        CompletableFuture<User> getUserAsync(@Path("id") Long userId);

        /** Multiple path params */
        @FeignMethod(method = HttpMethod.GET, path = {"users", "{id}", "posts", "{postId}"})
        Response getUserPost(@Path("id") Long userId, @Path("postId") Long postId);
    }

    // ── Custom load balancer ──
    static class StickyLoadBalancer implements LoadBalancer {
        private String stickyServer;

        @Override
        public String select(Request request, List<String> servers) {
            if (servers == null || servers.isEmpty()) throw new IllegalStateException("No servers");
            if (stickyServer == null || !servers.contains(stickyServer)) {
                stickyServer = servers.get(0);
            }
            return stickyServer;
        }
        @Override public void addServer(String url) {}
        @Override public void removeServer(String url) {}
        @Override public void reset() { stickyServer = null; }
        @Override public LoadBalancerType getType() { return LoadBalancerType.ROUND_ROBIN; }
    }

    // ── Interceptors with explicit ordering ──
    static class AuthInterceptor implements FeignInterceptor {
        @Override public int order() { return 0; }
        @Override public Request beforeExecute(Request r) {
            r.getHeaders().put("Authorization", "Bearer my-token");
            return r;
        }
    }

    static class LoggingInterceptor implements FeignInterceptor {
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
        // Build a fully customized client
        UserService service = new FeignClientFactory()
            // Custom decoder (default is GsonDecoder anyway, shown for explicitness)
            .decoder(new GsonDecoder(new Gson()))
            // Connection pool: 200 max, 20 per route
            .protocolHandler(new HttpProtocolHandler(5000, 10000, 200, 20))
            // Custom load balancer
            .loadBalancer(new StickyLoadBalancer())
            // Interceptors with explicit order
            .addInterceptor(new AuthInterceptor(), 0)
            .addInterceptor(new LoggingInterceptor(), 10)
            .build(UserService.class);

        System.out.println("=== Feign Framework Advanced Example ===");
        System.out.println("Proxy: " + service.getClass().getName());
        System.out.println();

        // Sync call — returns typed User object (decoded from JSON)
        try {
            User user = service.getUser(1L);
            System.out.println("Sync  result: " + user);
        } catch (FeignException e) {
            System.out.println("Expected (no server): " + e.getMessage());
        }

        // Async call — returns CompletableFuture<User>
        CompletableFuture<User> future = service.getUserAsync(2L);
        future.thenAccept(user -> System.out.println("Async result: " + user))
              .exceptionally(e -> { System.out.println("Async error: " + e.getMessage()); return null; });
    }
}
