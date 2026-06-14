package examples;

import com.feign.framework.*;
import com.feign.framework.annotations.*;
import com.feign.framework.http.*;
import com.feign.framework.interceptor.FeignInterceptor;
import com.feign.spring.EnableFeignClients;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Spring Boot 集成示例。
 *
 * <p>application.yml:
 * <pre>{@code
 * feign:
 *   client:
 *     config:
 *       default:
 *         url: http://localhost:8080
 *         connect-timeout: 5000
 *         read-timeout: 10000
 *         max-retries: 3
 *         load-balancer: ROUND_ROBIN
 *         decoder: jackson
 *         encoder: jackson
 *         interceptors: [loggingInterceptor, authInterceptor]
 *         circuit-breaker-enabled: true
 *         connection-pool:
 *           max-total: 200
 *           max-per-route: 20
 *         tracing:
 *           enabled: true
 *           service-name: demo-app
 *           sample-rate: 0.1
 *           reporter: logging
 *           log-enabled: true
 * }</pre>
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "examples")
public class SpringBootExample {

    public static void main(String[] args) { SpringApplication.run(SpringBootExample.class, args); }

    // ── Feign 客户端 ──
    @FeignClient(name = "user-service", url = "http://localhost:8080/api",
                 fallback = UserFallback.class)
    public interface UserService {
        @FeignMethod(method = HttpMethod.GET, path = {"users", "{id}"})
        User getUser(@Path("id") Long id);

        @FeignMethod(method = HttpMethod.POST, path = {"users"})
        User createUser(@Body User user, @Header("Authorization") String token);
    }

    public static class User { public Long id; public String name; public User(){} public User(Long id,String n){this.id=id;this.name=n;} }

    @Component
    public static class UserFallback implements UserService {
        @Override public User getUser(Long id) { return new User(id, "降级"); }
        @Override public User createUser(User u, String t) { return new User(0L, "降级"); }
    }

    // ── 拦截器（Spring Bean） ──
    @Component("authInterceptor")
    public static class AuthInterceptor implements FeignInterceptor {
        @Override public int order() { return 0; }
        @Override public Request beforeExecute(Request r) { r.getHeaders().put("X-Source", "feign"); return r; }
    }

    @Component("loggingInterceptor")
    public static class LogInterceptor implements FeignInterceptor {
        @Override public int order() { return 10; }
        @Override public Request beforeExecute(Request r) { System.out.println("[FEIGN] " + r.getMethod() + " " + r.getUrl()); return r; }
        @Override public Response afterExecute(Response r) { System.out.println("[FEIGN] " + r.getStatusCode()); return r; }
        @Override public void onError(Request r, FeignException e) { System.err.println("[FEIGN] " + e.getMessage()); }
    }

    // ── Controller ──
    @RestController
    static class UserController {
        @Autowired private UserService userService;

        @GetMapping("/proxy/user/{id}")
        public User getUser(@PathVariable Long id) { return userService.getUser(id); }
    }
}
