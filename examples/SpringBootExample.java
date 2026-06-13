package examples;

import com.feign.framework.annotations.FeignClient;
import com.feign.framework.annotations.FeignMethod;
import com.feign.framework.annotations.Path;
import com.feign.framework.http.HttpMethod;
import com.feign.framework.interceptor.FeignInterceptor;
import com.feign.framework.http.Request;
import com.feign.framework.Response;
import com.feign.framework.FeignException;
import com.feign.spring.EnableFeignClients;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

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
 *         decoder: gson
 *         interceptors:
 *           - loggingInterceptor
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

    // ── FeignClient interfaces — auto-registered as Beans ──

    @FeignClient(name = "user-service", url = "http://localhost:8080/api")
    public interface UserService {
        @FeignMethod(method = HttpMethod.GET, path = {"users", "{id}"})
        Map<String, Object> getUser(@Path("id") Long id);

        @FeignMethod(method = HttpMethod.POST, path = {"users"})
        Map<String, Object> createUser(Map<String, Object> user);
    }

    @FeignClient(name = "order-service", url = "http://localhost:8080/orders",
                 loadBalancer = com.feign.framework.loadbalancer.LoadBalancerType.RANDOM,
                 maxRetries = 5)
    public interface OrderService {
        @FeignMethod(method = HttpMethod.GET, path = {"{id}"})
        Map<String, Object> getOrder(@Path("id") Long id);
    }

    // ── Interceptor — registered as Spring Bean, referenced in config ──

    @Component("loggingInterceptor")
    public static class LoggingInterceptor implements FeignInterceptor {
        @Override public int order() { return 0; }

        @Override
        public Request beforeExecute(Request request) {
            System.out.println("[FEIGN] → " + request.getMethod() + " " + request.getUrl());
            return request;
        }

        @Override
        public Response afterExecute(Response response) {
            System.out.println("[FEIGN] ← " + response.getStatusCode());
            return response;
        }

        @Override
        public void onError(Request request, FeignException exception) {
            System.err.println("[FEIGN] ✗ " + exception.getMessage());
        }
    }

    // ── RestController — uses @Autowired Feign client ──

    @RestController
    public static class UserController {

        @Autowired
        private UserService userService;  // ← Spring 自动注入代理

        @Autowired
        private OrderService orderService; // ← 多个 Feign 客户端一起用

        @GetMapping("/proxy/user")
        public Object getUser() {
            return userService.getUser(1L);
        }

        @GetMapping("/proxy/order")
        public Object getOrder() {
            return orderService.getOrder(100L);
        }
    }
}
