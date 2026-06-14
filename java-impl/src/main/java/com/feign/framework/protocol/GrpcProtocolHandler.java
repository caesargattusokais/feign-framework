package com.feign.framework.protocol;

import com.feign.framework.FeignException;
import com.feign.framework.Response;
import com.feign.framework.http.Request;
import com.google.gson.Gson;
import io.grpc.ConnectivityState;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

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

    private static final Logger log = Logger.getLogger(GrpcProtocolHandler.class.getName());

    private final Map<String, ChannelEntry> channels = new ConcurrentHashMap<>();
    private final ScheduledExecutorService healthChecker;
    private final Gson gson = new Gson();
    private final long callTimeoutMs;
    private final long keepAliveSec;
    private final long keepAliveTimeoutSec;
    private final long idleTimeoutSec;

    /** Default: 5s call timeout, 30s keep-alive, 10s keep-alive timeout, 300s idle */
    public GrpcProtocolHandler() {
        this(5000, 30, 10, 300);
    }

    public GrpcProtocolHandler(int callTimeoutMs) {
        this(callTimeoutMs, 30, 10, 300);
    }

    /**
     * @param callTimeoutMs       RPC call timeout in ms
     * @param keepAliveSec        send keep-alive PING after this many seconds
     * @param keepAliveTimeoutSec wait for PING ACK before closing channel
     * @param idleTimeoutSec      close idle channel after this many seconds; 0 = never
     */
    public GrpcProtocolHandler(long callTimeoutMs, long keepAliveSec,
                                long keepAliveTimeoutSec, long idleTimeoutSec) {
        this.callTimeoutMs = callTimeoutMs;
        this.keepAliveSec = keepAliveSec;
        this.keepAliveTimeoutSec = keepAliveTimeoutSec;
        this.idleTimeoutSec = idleTimeoutSec;
        this.healthChecker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "grpc-health");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public String scheme() {
        return "grpc";
    }

    @Override
    public Response execute(Request request) throws Exception {
        GrpcTarget target = parseTarget(request);
        ChannelEntry entry = getOrCreateChannel(target.host, target.port);
        entry.touch();

        String fullMethod = target.serviceName + "/" + target.methodName;
        byte[] payload = request.getBody() != null ? request.getBody() : new byte[0];

        MethodDescriptor.Marshaller<byte[]> m = byteMarshaller();
        MethodDescriptor<byte[], byte[]> methodDesc =
                MethodDescriptor.<byte[], byte[]>newBuilder()
                        .setType(MethodDescriptor.MethodType.UNARY)
                        .setFullMethodName(fullMethod)
                        .setRequestMarshaller(m)
                        .setResponseMarshaller(m)
                        .build();

        ClientCall<byte[], byte[]> call = entry.channel.newCall(methodDesc,
                CallOptions.DEFAULT.withDeadlineAfter(callTimeoutMs, TimeUnit.MILLISECONDS));

        CompletableFuture<byte[]> future = new CompletableFuture<>();
        call.start(new ClientCall.Listener<>() {
            @Override public void onMessage(byte[] msg) { future.complete(msg); }
            @Override
            public void onClose(io.grpc.Status s, Metadata t) {
                if (!s.isOk() && !future.isDone())
                    future.completeExceptionally(new StatusRuntimeException(s, t));
            }
        }, new Metadata());

        call.sendMessage(payload);
        call.halfClose();
        call.request(1);

        try {
            byte[] responseBytes = future.get(callTimeoutMs, TimeUnit.MILLISECONDS);
            return Response.of(request.getUrl(), 200, new HashMap<>(),
                new String(responseBytes, StandardCharsets.UTF_8));
        } catch (TimeoutException e) {
            throw new FeignException("gRPC call timeout: " + fullMethod);
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
            GrpcTarget target = parseTargetFromUrl(url);
            ChannelEntry entry = channels.get(target.host + ":" + target.port);
            if (entry == null) return false;
            ConnectivityState s = entry.channel.getState(false);
            return s != ConnectivityState.SHUTDOWN && s != ConnectivityState.TRANSIENT_FAILURE;
        } catch (Exception e) {
            return false;
        }
    }

    public void shutdown() {
        healthChecker.shutdownNow();
        channels.values().forEach(e -> {
            e.channel.shutdown();
            if (e.healthFuture != null) e.healthFuture.cancel(false);
        });
        channels.clear();
    }

    // ── helpers ──

    private GrpcTarget parseTarget(Request request) {
        return parseTargetFromUrl(request.getUrl());
    }

    private GrpcTarget parseTargetFromUrl(String url) {
        String withoutScheme = url.replaceFirst("^grpc://", "");
        String[] parts = withoutScheme.split("/", 3);
        if (parts.length < 3) {
            throw new FeignException(
                "Invalid gRPC URL. Expected: grpc://host:port/serviceName/methodName");
        }
        String[] hostPort = parts[0].split(":");
        String host = hostPort[0];
        int port = hostPort.length > 1 ? Integer.parseInt(hostPort[1]) : 50051;
        return new GrpcTarget(host, port, parts[1], parts[2]);
    }

    private ChannelEntry getOrCreateChannel(String host, int port) {
        String key = host + ":" + port;
        return channels.computeIfAbsent(key, k -> createChannel(host, port));
    }

    private ChannelEntry createChannel(String host, int port) {
        ChannelEntry entry = new ChannelEntry();

        ManagedChannel ch = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .keepAliveTime(keepAliveSec, TimeUnit.SECONDS)
                .keepAliveTimeout(keepAliveTimeoutSec, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                .idleTimeout(idleTimeoutSec, TimeUnit.SECONDS)
                .build();

        entry.channel = ch;

        // Health check: periodically monitor connectivity state
        entry.healthFuture = healthChecker.scheduleWithFixedDelay(() -> {
            ConnectivityState state = ch.getState(false);

            if (state == ConnectivityState.TRANSIENT_FAILURE) {
                log.warning("gRPC TRANSIENT_FAILURE " + host + ":" + port + " — resetting backoff");
                ch.resetConnectBackoff();
            }
            if (state == ConnectivityState.SHUTDOWN) {
                channels.remove(host + ":" + port);
                log.info("gRPC SHUTDOWN " + host + ":" + port);
            }
            if (idleTimeoutSec > 0) {
                long idle = System.currentTimeMillis() - entry.lastUsed.get();
                if (idle > idleTimeoutSec * 1000L) {
                    log.info("gRPC idle timeout " + host + ":" + port);
                    ch.shutdown();
                    channels.remove(host + ":" + port);
                }
            }
            // Trigger connectivity check (forces keep-alive PING)
            ch.getState(true);

        }, keepAliveSec, keepAliveSec, TimeUnit.SECONDS);

        return entry;
    }

    private static MethodDescriptor.Marshaller<byte[]> byteMarshaller() {
        return new MethodDescriptor.Marshaller<>() {
            @Override public InputStream stream(byte[] value) {
                return new ByteArrayInputStream(value); }
            @Override public byte[] parse(InputStream stream) {
                try { return stream.readAllBytes(); }
                catch (java.io.IOException e) { throw new RuntimeException(e); }
            }
        };
    }

    // ── inner classes ──

    private static class ChannelEntry {
        volatile ManagedChannel channel;
        volatile ScheduledFuture<?> healthFuture;
        final AtomicLong lastUsed = new AtomicLong(System.currentTimeMillis());
        void touch() { lastUsed.set(System.currentTimeMillis()); }
    }

    private record GrpcTarget(String host, int port, String serviceName, String methodName) {}
}
