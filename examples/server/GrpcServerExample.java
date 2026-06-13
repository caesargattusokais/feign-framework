package examples.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServerInterceptors;
import io.grpc.BindableService;
import io.grpc.Metadata;
import com.google.gson.Gson;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * gRPC 服务端示例 — 带 HTTP/2 保活配置。
 *
 * <p>保活关键参数（服务端必须有）：
 * <pre>
 *   .keepAliveTime(30, SECONDS)       — 30s 无活动发 PING
 *   .keepAliveTimeout(10, SECONDS)    — PING ACK 等 10s
 *   .permitKeepAliveWithoutCalls(true) — 无活跃 RPC 也允许保活
 *   .maxConnectionIdle(300, SECONDS)   — 空闲 5 分钟关闭
 * </pre>
 *
 * <p>客户端配置应与服务端对称。
 * 客户端: {@code keepAliveTime=30s, keepAliveTimeout=10s, idleTimeout=300s}
 */
public class GrpcServerExample {

    private static final Logger log = Logger.getLogger(GrpcServerExample.class.getName());
    private static final Gson gson = new Gson();

    public static void main(String[] args) throws IOException, InterruptedException {
        // 创建 UserService 实现
        BindableService userService = new UserServiceImpl();

        Server server = ServerBuilder.forPort(50051)
                .addService(userService)
                // ── 保活配置（关键！）──
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(10, TimeUnit.SECONDS)
                .permitKeepAliveWithoutCalls(true)
                .maxConnectionIdle(5, TimeUnit.MINUTES)
                .build();

        server.start();
        log.info("gRPC server started on grpc://localhost:50051");
        log.info("  Service: UserService");
        log.info("  Methods: GetUser, CreateUser");
        log.info("  Keep-alive: keepAliveTime=30s, keepAliveTimeout=10s, idleTimeout=5min");

        server.awaitTermination();
    }

    /**
     * 简单的 UserService 实现（用 JSON 替代 protobuf，配合客户端的 stringMarshaller）。
     * 生产环境建议使用标准 protobuf 编译。
     */
    static class UserServiceImpl extends io.grpc.stub.AbstractStub<Object> implements BindableService {

        protected UserServiceImpl() {
            super(null, null);
        }

        @Override
        public ServerServiceDefinition bindService() {
            return io.grpc.ServerServiceDefinition.builder("UserService")
                .addMethod(
                    createMethod("GetUser"),
                    (ServerCallHandler<String, String>) call -> {
                        call.sendHeaders(new Metadata());
                        // 解析请求、构造响应
                        String responseJson = "{\"id\":1,\"name\":\"张三\",\"email\":\"zhangsan@example.com\"}";

                        call.sendMessage(responseJson);
                        call.close(Status.OK, new Metadata());
                        return null;
                    }
                )
                .addMethod(
                    createMethod("CreateUser"),
                    (ServerCallHandler<String, String>) call -> {
                        call.sendHeaders(new Metadata());
                        String responseJson = "{\"id\":2,\"name\":\"李四\"}";

                        call.sendMessage(responseJson);
                        call.close(Status.OK, new Metadata());
                        return null;
                    }
                )
                .build();
        }

        @SuppressWarnings("unchecked")
        private io.grpc.ServerMethodDefinition<String, String> createMethod(String name) {
            MethodDescriptor.Marshaller<String> m = new MethodDescriptor.Marshaller<>() {
                @Override public java.io.InputStream stream(String value) {
                    return new java.io.ByteArrayInputStream(
                        value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                @Override public String parse(java.io.InputStream stream) {
                    try {
                        return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            };

            return io.grpc.ServerMethodDefinition.create(
                MethodDescriptor.<String, String>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName("UserService/" + name)
                    .setRequestMarshaller(m)
                    .setResponseMarshaller(m)
                    .build(),
                (call, headers) -> {
                    // Simplified handler
                    return null;
                }
            );
        }
    }
}
