package com.feign.framework.protocol;

import com.feign.framework.FeignException;
import com.feign.framework.Response;
import com.feign.framework.http.Request;
import com.google.gson.Gson;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.MethodDescriptor;
import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;

/**
 * gRPC protocol handler.
 *
 * <p>Maps @FeignClient(url = "grpc://host:port") calls to gRPC requests.
 * Uses JSON serialization (no protobuf compilation required) —
 * compatible with gRPC-web / grpc-gateway style backends.
 *
 * <p>Usage:
 * <pre>{@code
 * @FeignClient(name = "user-rpc", url = "grpc://localhost:50051")
 * public interface UserGrpc {
 *     @FeignMethod(method = HttpMethod.POST, path = {"UserService", "GetUser"})
 *     Map<String, Object> getUser(@Path("id") String id);
 * }
 * }</pre>
 *
 * <p>The path segments are interpreted as {serviceName, methodName}.
 * Request body is serialized to JSON and sent as gRPC payload.
 */
public class GrpcProtocolHandler implements ProtocolHandler {

    private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();
    private final int timeoutMs;

    public GrpcProtocolHandler() {
        this(5000);
    }

    public GrpcProtocolHandler(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    @Override
    public String scheme() {
        return "grpc";
    }

    @Override
    public Response execute(Request request) throws Exception {
        GrpcTarget target = parseTarget(request);
        ManagedChannel channel = getOrCreateChannel(target.host, target.port);

        // Build JSON payload from request body
        String jsonPayload = request.getBody() != null
                ? new String(request.getBody())
                : "{}";

        // Call gRPC using a generic unary call
        // Format: POST /package.ServiceName/MethodName
        String fullMethodName = target.serviceName + "/" + target.methodName;

        // Use a blocking unary call via gRPC's generic method descriptors
        // For simplicity, we serialize to JSON, call, and deserialize response
        io.grpc.MethodDescriptor.Marshaller<String> marshaller =
                createStringMarshaller();

        MethodDescriptor<String, String> methodDesc =
                MethodDescriptor.<String, String>newBuilder()
                        .setType(MethodDescriptor.MethodType.UNARY)
                        .setFullMethodName(fullMethodName)
                        .setRequestMarshaller(marshaller)
                        .setResponseMarshaller(marshaller)
                        .build();

        // Execute blocking unary call
        ClientCall<String, String> call = channel.newCall(methodDesc, CallOptions.DEFAULT);
        String[] responseHolder = new String[1];
        Exception[] errorHolder = new Exception[1];

        CompletableFuture<String> future = new CompletableFuture<>();
        call.start(new ClientCall.Listener<String>() {
            @Override
            public void onMessage(String message) {
                future.complete(message);
            }

            @Override
            public void onClose(io.grpc.Status status, Metadata trailers) {
                if (!status.isOk() && !future.isDone()) {
                    future.completeExceptionally(
                            new StatusRuntimeException(status, trailers));
                }
            }
        }, new Metadata());

        call.sendMessage(jsonPayload);
        call.halfClose();
        call.request(1);

        try {
            String responseJson = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            return Response.of(request.getUrl(), 200, new HashMap<>(),
                    responseJson);
        } catch (Exception e) {
            throw new FeignException("gRPC call failed: " + fullMethodName, e);
        }
    }

    @Override
    public CompletableFuture<Response> executeAsync(Request request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return execute(request);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public boolean isAvailable(String url) {
        try {
            GrpcTarget target = parseTarget(url);
            ManagedChannel channel = getOrCreateChannel(target.host, target.port);
            return !channel.isShutdown() && !channel.isTerminated();
        } catch (Exception e) {
            return false;
        }
    }

    // --- helpers ---

    private GrpcTarget parseTarget(Request request) {
        return parseTarget(request.getUrl());
    }

    private GrpcTarget parseTarget(String url) {
        // url format: grpc://host:port/serviceName/methodName
        String withoutScheme = url.replaceFirst("^grpc://", "");
        String[] parts = withoutScheme.split("/", 3);
        if (parts.length < 3) {
            throw new FeignException("Invalid gRPC URL format. Expected: grpc://host:port/serviceName/methodName");
        }
        String[] hostPort = parts[0].split(":");
        String host = hostPort[0];
        int port = hostPort.length > 1 ? Integer.parseInt(hostPort[1]) : 50051;
        return new GrpcTarget(host, port, parts[1], parts[2]);
    }

    private ManagedChannel getOrCreateChannel(String host, int port) {
        String key = host + ":" + port;
        return channels.computeIfAbsent(key, k ->
                ManagedChannelBuilder.forAddress(host, port)
                        .usePlaintext()
                        .build());
    }

    private static io.grpc.MethodDescriptor.Marshaller<String> createStringMarshaller() {
        return new io.grpc.MethodDescriptor.Marshaller<>() {
            @Override
            public InputStream stream(String value) {
                return new ByteArrayInputStream(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            @Override
            public String parse(InputStream stream) {
                try {
                    return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                } catch (java.io.IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    private static class GrpcTarget {
        final String host;
        final int port;
        final String serviceName;
        final String methodName;

        GrpcTarget(String host, int port, String serviceName, String methodName) {
            this.host = host;
            this.port = port;
            this.serviceName = serviceName;
            this.methodName = methodName;
        }
    }
}
