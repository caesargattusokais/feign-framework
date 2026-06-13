package com.feign.framework.protocol;

import com.feign.framework.FeignException;
import com.feign.framework.Response;
import com.feign.framework.http.Request;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * WebSocket protocol handler with keep-alive and auto-reconnection.
 *
 * <p>Keep-alive mechanism:
 * <ul>
 *   <li>Sends ping frames every {@code pingInterval} seconds</li>
 *   <li>If no pong received within {@code pongTimeout} seconds, marks connection dead</li>
 *   <li>Dead connections trigger automatic reconnection on next request</li>
 *   <li>Connections idle for longer than {@code idleTimeout} seconds are closed</li>
 * </ul>
 */
public class WebSocketProtocolHandler implements ProtocolHandler {

    private static final Logger log = Logger.getLogger(WebSocketProtocolHandler.class.getName());

    private final Map<String, WebSocketConnection> connections = new ConcurrentHashMap<>();
    private final Map<String, Consumer<String>> messageHandlers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatExecutor;
    private final int connectTimeoutSec;
    private final int pingIntervalSec;
    private final int pongTimeoutSec;
    private final int idleTimeoutSec;

    public WebSocketProtocolHandler() {
        this(10, 20, 5, 120);
    }

    /**
     * @param connectTimeoutSec connection timeout in seconds
     * @param pingIntervalSec   how often to send ping (seconds)
     * @param pongTimeoutSec    how long to wait for pong before declaring dead (seconds)
     * @param idleTimeoutSec    close connection after this much idle time (seconds); 0 = never
     */
    public WebSocketProtocolHandler(int connectTimeoutSec, int pingIntervalSec,
                                     int pongTimeoutSec, int idleTimeoutSec) {
        this.connectTimeoutSec = connectTimeoutSec;
        this.pingIntervalSec = pingIntervalSec;
        this.pongTimeoutSec = pongTimeoutSec;
        this.idleTimeoutSec = idleTimeoutSec;
        this.heartbeatExecutor = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "ws-heartbeat");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public String scheme() { return "ws"; }

    @Override
    public Response execute(Request request) throws Exception {
        String key = connectionKey(request.getUrl());
        WebSocketConnection conn = getOrConnect(key, request.getUrl());

        String message = request.getBody() != null ? new String(request.getBody()) : "";

        CompletableFuture<String> responseFuture = new CompletableFuture<>();
        conn.setPendingResponse(responseFuture);
        conn.touch(); // reset idle timer

        conn.webSocket.sendText(message, true);

        try {
            String reply = responseFuture.get(connectTimeoutSec, TimeUnit.SECONDS);
            return Response.of(request.getUrl(), 200, new HashMap<>(), reply);
        } catch (TimeoutException e) {
            return Response.of(request.getUrl(), 200, new HashMap<>(), null);
        }
    }

    @Override
    public CompletableFuture<Response> executeAsync(Request request) {
        return CompletableFuture.supplyAsync(() -> {
            try { return execute(request); }
            catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    @Override
    public boolean isAvailable(String url) {
        WebSocketConnection conn = connections.get(connectionKey(url));
        return conn != null && conn.isAlive();
    }

    public void onMessage(String url, Consumer<String> handler) {
        messageHandlers.put(connectionKey(url), handler);
    }

    public void shutdown() {
        heartbeatExecutor.shutdownNow();
        connections.values().forEach(conn -> conn.close(1000, "shutdown"));
        connections.clear();
    }

    // ── internals ──

    private String connectionKey(String url) {
        return url.replaceFirst("^wss?://", "");
    }

    private WebSocketConnection getOrConnect(String key, String url) throws Exception {
        // Remove dead connections
        WebSocketConnection existing = connections.get(key);
        if (existing != null && !existing.isAlive()) {
            connections.remove(key);
        }

        return connections.computeIfAbsent(key, k -> {
            try {
                return doConnect(key, url);
            } catch (Exception e) {
                throw new RuntimeException("WebSocket connect failed: " + url, e);
            }
        });
    }

    private WebSocketConnection doConnect(String key, String url) throws Exception {
        URI wsUri = new URI(url);
        WebSocketConnection conn = new WebSocketConnection();

        CompletableFuture<WebSocket> wsFuture;
        try (HttpClient httpClient = HttpClient.newHttpClient()) {
            wsFuture = httpClient.newWebSocketBuilder()
                    .buildAsync(wsUri, new WebSocket.Listener() {
                        @Override
                        public void onOpen(WebSocket ws) {
                            WebSocket.Listener.super.onOpen(ws);
                            conn.markAlive();
                            log.fine("WebSocket connected: " + key);
                        }

                        @Override
                        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                            String msg = data.toString();

                            CompletableFuture<String> pending = conn.takePendingResponse();
                            if (pending != null) {
                                pending.complete(msg);
                            }

                            Consumer<String> handler = messageHandlers.get(key);
                            if (handler != null) {
                                handler.accept(msg);
                            }
                            return null;
                        }

                        @Override
                        public CompletionStage<?> onPong(WebSocket ws, ByteBuffer message) {
                            conn.markAlive(); // pong received → connection is healthy
                            return null;
                        }

                        @Override
                        public void onError(WebSocket ws, Throwable error) {
                            log.warning("WebSocket error: " + key + " — " + error.getMessage());
                            conn.markDead();
                        }

                        @Override
                        public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
                            log.info("WebSocket closed: " + key + " code=" + code + " " + reason);
                            conn.markDead();
                            connections.remove(key);
                            return null;
                        }
                    });
        }

        conn.webSocket = wsFuture.get(connectTimeoutSec, TimeUnit.SECONDS);

        // Schedule heartbeat pings
        conn.heartbeatFuture = heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (!conn.isAlive()) {
                // Connection dead — cancel heartbeat
                return;
            }

            // Check idle timeout
            if (idleTimeoutSec > 0) {
                long idleMs = System.currentTimeMillis() - conn.lastActivityTime.get();
                if (idleMs > idleTimeoutSec * 1000L) {
                    log.info("WebSocket idle timeout: " + key);
                    conn.close(1000, "idle timeout");
                    connections.remove(key); // allow reconnect on next request
                    return;
                }
            }

            // Ping-pong health check
            long sinceLastAlive = System.currentTimeMillis() - conn.lastAliveTime.get();
            if (sinceLastAlive > pongTimeoutSec * 1000L) {
                log.warning("WebSocket pong timeout: " + key);
                conn.markDead();
                connections.remove(key); // allow reconnect
                return;
            }

            // Send ping
            try {
                conn.webSocket.sendPing(ByteBuffer.allocate(0));
            } catch (Exception e) {
                conn.markDead();
            }
        }, pingIntervalSec, pingIntervalSec, TimeUnit.SECONDS);

        return conn;
    }

    // ── inner class ──

    private static class WebSocketConnection {
        volatile WebSocket webSocket;
        volatile ScheduledFuture<?> heartbeatFuture;
        volatile CompletableFuture<String> pendingResponse;
        final AtomicLong lastAliveTime = new AtomicLong(System.currentTimeMillis());
        final AtomicLong lastActivityTime = new AtomicLong(System.currentTimeMillis());
        volatile boolean alive = true;

        void markAlive() { lastAliveTime.set(System.currentTimeMillis()); }
        void markDead() {
            alive = false;
            if (heartbeatFuture != null) heartbeatFuture.cancel(false);
        }
        void touch() { lastActivityTime.set(System.currentTimeMillis()); }
        boolean isAlive() { return alive && webSocket != null && !webSocket.isOutputClosed(); }

        synchronized void setPendingResponse(CompletableFuture<String> f) { pendingResponse = f; }
        synchronized CompletableFuture<String> takePendingResponse() {
            CompletableFuture<String> f = pendingResponse; pendingResponse = null; return f;
        }

        void close(int code, String reason) {
            alive = false;
            if (heartbeatFuture != null) heartbeatFuture.cancel(false);
            try { webSocket.sendClose(code, reason); } catch (Exception ignored) {}
        }
    }
}
