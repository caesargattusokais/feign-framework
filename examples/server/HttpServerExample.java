package examples.server;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * HTTP 服务端示例。
 *
 * <p>HTTP 是短连接协议，不需要额外保活配置。
 * JDK 内置的 HttpServer 会自动处理 Connection: keep-alive 头。
 *
 * <p>启动后访问: http://localhost:8080/api/users/1
 */
public class HttpServerExample {

    private static final Gson gson = new Gson();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        // GET /api/users/{id}
        server.createContext("/api/users/", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                String path = exchange.getRequestURI().getPath();
                String id = path.substring(path.lastIndexOf('/') + 1);

                Map<String, Object> user = Map.of(
                    "id", Long.parseLong(id),
                    "name", "张三",
                    "email", "zhangsan@example.com"
                );

                sendJson(exchange, 200, gson.toJson(user));
            } else {
                sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        });

        // POST /api/users
        server.createContext("/api/users", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, Object> created = Map.of(
                    "id", 1L,
                    "body", body
                );
                sendJson(exchange, 201, gson.toJson(created));
            } else {
                sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        });

        // 线程池（支持并发 + keep-alive）
        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();

        System.out.println("HTTP server started on http://localhost:8080");
        System.out.println("  GET  /api/users/{id}  → 返回用户 JSON");
        System.out.println("  POST /api/users       → 创建用户");
        System.out.println("  Keep-alive: JDK HttpServer 默认支持");
    }

    private static void sendJson(HttpExchange exchange, int code, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
