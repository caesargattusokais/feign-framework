package examples;

import com.feign.framework.FeignException;
import com.feign.framework.Response;
import com.feign.framework.annotations.FeignClient;
import com.feign.framework.annotations.FeignMethod;
import com.feign.framework.annotations.Path;
import com.feign.framework.http.HttpMethod;
import com.feign.framework.http.Request;
import com.feign.framework.interceptor.FeignInterceptor;
import com.feign.framework.codec.Encoder;
import com.feign.framework.discovery.ServiceDiscovery;
import com.feign.spring.EnableFeignClients;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Spring Boot 集成示例 — Encoder + Fallback + ServiceDiscovery + 拦截器 + 负载均衡。
 *
 * <p>application.yml:
 * <pre>{@code
 * feign:
 *   client:
 *     config:
 *       default:
 *         connect-timeout: 5000
 *         read-timeout: 10000
 *         max-retries: 3
 *         load-balancer: ROUND_ROBIN
 *         encoder: gson
 *         decoder: gson
 *         interceptors: [loggingInterceptor]
 *         discovery-bean: myServiceDiscovery
 *         connection-pool:
 *           max-total: 200
 *           max-per-route: 20
 * }</pre>
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "examples")
public class SpringBootExample {

    public static void main(String[] args) {
        SpringApplication.run(SpringBootExample.class, args);
    }

    // ══════════════════════════════════════════
    //  Feign 客户端 — 自动变成 Spring Bean
    // ══════════════════════════════════════════

    /** 带 fallback 的客户端 */
    @FeignClient(name = "user-service", url = "http://localhost:8080/api",
                 fallback = UserServiceFallback.class)
    public interface UserService {
        @FeignMethod(method = HttpMethod.GET, path = {"users", "{id}"})
        Map<String, Object> getUser(@Path("id") Long id);

        @FeignMethod(method = HttpMethod.POST, path = {"users"})
        Map<String, Object> createUser(User user);  // ← Encoder 自动序列化 User
    }

    /** 使用服务发现，不写死 URL */
    @FeignClient(name = "order-service")  // URL 由 ServiceDiscovery 提供
    public interface OrderService {
        @FeignMethod(method = HttpMethod.GET, path = {"orders", "{id}"})
        Map<String, Object> getOrder(@Path("id") Long id);
    }

    // ══════════════════════════════════════════
    //  Fallback 实现
    // ══════════════════════════════════════════

    @Component
    public static class UserServiceFallback implements UserService {
        @Override public Map<String, Object> getUser(Long id) {
            return Map.of("id", id, "name", "fallback-user", "source", "fallback");
        }
        @Override public Map<String, Object> createUser(User user) {
            return Map.of("status", "fallback", "msg", "服务降级");
        }
    }

    // ══════════════════════════════════════════
    //  领域模型 — Encoder 自动序列化
    // ══════════════════════════════════════════

    public static class User {
        public Long id;
        public String name;
        public String email;
        public User() {}
        public User(Long id, String name) { this.id = id; this.name = name; }
    }

    // ══════════════════════════════════════════
    //  服务发现（模拟实现）
    // ══════════════════════════════════════════

    @Bean("myServiceDiscovery")
    public ServiceDiscovery serviceDiscovery() {
        return new ServiceDiscovery() {
            private final Map<String, List<String>> registry = Map.of(
                "user-service", List.of("http://user-api:8080", "http://user-api:8081"),
                "order-service", List.of("http://order-api:9090")
            );
            @Override public List<String> getInstances(String name) {
                return registry.getOrDefault(name, List.of());
            }
        };
    }

    // ══════════════════════════════════════════
    //  拦截器（Spring Bean）
    // ══════════════════════════════════════════

    @Component("loggingInterceptor")
    public static class LoggingInterceptor implements FeignInterceptor {
        @Override public int order() { return 0; }
        @Override public Request beforeExecute(Request r) {
            System.out.println("[FEIGN] → " + r.getMethod() + " " + r.getUrl());
            return r;
        }
        @Override public Response afterExecute(Response r) {
            System.out.println("[FEIGN] ← " + r.getStatusCode());
            return r;
        }
        @Override public void onError(Request r, FeignException e) {
            System.err.println("[FEIGN] ✗ " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════
    //  RestController — @Autowired 注入
    // ══════════════════════════════════════════

    @RestController
    public static class UserController {
        @Autowired private UserService userService;
        @Autowired private OrderService orderService;

        @GetMapping("/proxy/user")
        public Object getUser() { return userService.getUser(1L); }

        @GetMapping("/proxy/order")
        public Object getOrder() { return orderService.getOrder(100L); }
    }
}
