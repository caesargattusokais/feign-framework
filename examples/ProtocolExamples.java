package examples;

import com.feign.framework.annotations.FeignClient;
import com.feign.framework.annotations.FeignMethod;
import com.feign.framework.annotations.Path;
import com.feign.framework.http.HttpMethod;
import com.feign.processor.FeignClientFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 多协议客户端示例 — HTTP / gRPC / WebSocket。
 * 同一个注解、同一个工厂，只改 URL scheme 就切换协议。
 */
public class ProtocolExamples {

    // ══════════════════════════════════════════════════════
    //  HTTP 协议（默认）
    // ══════════════════════════════════════════════════════
    @FeignClient(name = "user-service", url = "http://localhost:8080/api")
    public interface UserHttpService {
        @FeignMethod(method = HttpMethod.GET, path = {"users", "{id}"})
        Map<String, Object> getUser(@Path("id") Long id);

        @FeignMethod(method = HttpMethod.POST, path = {"users"})
        Map<String, Object> createUser(Map<String, Object> user);
    }

    // ══════════════════════════════════════════════════════
    //  gRPC 协议 — 改 scheme 为 grpc:// 即可
    //  path 格式: {serviceName}/{methodName}
    // ══════════════════════════════════════════════════════
    @FeignClient(name = "user-rpc", url = "grpc://localhost:50051")
    public interface UserGrpcService {
        @FeignMethod(method = HttpMethod.POST, path = {"UserService", "GetUser"})
        Map<String, Object> getUser(@Path("id") Long id);

        @FeignMethod(method = HttpMethod.POST, path = {"UserService", "CreateUser"})
        Map<String, Object> createUser(Map<String, Object> user);
    }

    // ══════════════════════════════════════════════════════
    //  WebSocket 协议 — 改 scheme 为 ws:// 即可
    // ══════════════════════════════════════════════════════
    @FeignClient(name = "chat-service", url = "ws://localhost:8080/chat")
    public interface ChatWsService {
        @FeignMethod(method = HttpMethod.POST, path = {"send"})
        void sendMessage(String message);

        @FeignMethod(method = HttpMethod.POST, path = {"broadcast"})
        void broadcast(String message);
    }

    // ── 演示入口 ──

    public static void main(String[] args) throws Exception {
        System.out.println("=== Feign Framework 多协议客户端示例 ===\n");

        // ── HTTP ──
        System.out.println("1. HTTP 协议:");
        UserHttpService httpSvc = FeignClientFactory.create(UserHttpService.class);
        System.out.println("   URL: http://localhost:8080/api");
        System.out.println("   Proxy: " + httpSvc.getClass().getName());
        try {
            Map<String, Object> user = httpSvc.getUser(1L);
            System.out.println("   getUser(1) → " + user);
        } catch (Exception e) {
            System.out.println("   (服务未启动) " + e.getMessage());
        }

        // ── gRPC ──
        System.out.println("\n2. gRPC 协议:");
        UserGrpcService grpcSvc = FeignClientFactory.create(UserGrpcService.class);
        System.out.println("   URL: grpc://localhost:50051");
        System.out.println("   Proxy: " + grpcSvc.getClass().getName());
        try {
            Map<String, Object> result = grpcSvc.getUser(1L);
            System.out.println("   get_user(1) → " + result);
        } catch (Exception e) {
            System.out.println("   (服务未启动) " + e.getMessage());
        }

        // ── WebSocket ──
        System.out.println("\n3. WebSocket 协议:");
        ChatWsService wsSvc = FeignClientFactory.create(ChatWsService.class);
        System.out.println("   URL: ws://localhost:8080/chat");
        System.out.println("   Proxy: " + wsSvc.getClass().getName());
        try {
            wsSvc.sendMessage("Hello from Feign!");
            System.out.println("   sendMessage() → sent (fire-and-forget)");
        } catch (Exception e) {
            System.out.println("   (服务未启动) " + e.getMessage());
        }
    }
}
