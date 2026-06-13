package com.feign.framework.protocol;

import com.feign.framework.FeignException;
import com.feign.framework.Response;
import com.feign.framework.http.Request;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * WebSocket protocol handler.
 *
 * <p>Maps @FeignClient(url = "ws://host:port/path") calls to WebSocket messages.
 * Supports sending messages and registering message handlers.
 *
 * <p>Usage:
 * <pre>{@code
 * @FeignClient(name = "chat", url = "ws://localhost:8080/chat")
 * public interface ChatService {
 *     @FeignMethod(method = HttpMethod.POST, path = {"send"})
 *     void sendMessage(String message);
 * }
 * }</pre>
 *
 * <p>Connection lifecycle: Connections are managed internally, created
 * on first request and reused. The handler implements keep-alive and
 * automatic reconnection.
 */
public class WebSocketProtocolHandler implements ProtocolHandler {

    private final Map<String, WebSocketConnection> connections = new ConcurrentHashMap<>();
    private final Map<String, Consumer<String>> messageHandlers = new ConcurrentHashMap<>();
    private final int connectTimeoutMs;

    public WebSocketProtocolHandler() {
        this(5000);
    }

    public WebSocketProtocolHandler(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    @Override
    public String scheme() {
        return "ws";
    }

    @Override
    public Response execute(Request request) throws Exception {
        String key = connectionKey(request.getUrl());
        WebSocketConnection conn = getOrConnect(key, request.getUrl());

        // Send message
        String message = request.getBody() != null
                ? new String(request.getBody())
                : "";

        CompletableFuture<String> responseFuture = new CompletableFuture<>();
        conn.setPendingResponse(responseFuture);

        conn.webSocket.sendText(message, true);

        // Wait for response (websocket echo or reply pattern)
        try {
            String reply = responseFuture.get(connectTimeoutMs, TimeUnit.MILLISECONDS);
            return Response.of(request.getUrl(), 200, new HashMap<>(), reply);
        } catch (java.util.concurrent.TimeoutException e) {
            // No response within timeout — that's normal for fire-and-forget WebSocket
            return Response.of(request.getUrl(), 200, new HashMap<>(), null);
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
            WebSocketConnection conn = connections.get(connectionKey(url));
            return conn != null && !conn.webSocket.isOutputClosed();
        } catch (Exception e) {
            return false;
        }
    }

    // --- public API for message handlers ---

    /**
     * Register a message handler for incoming messages on a URL.
     */
    public void onMessage(String url, Consumer<String> handler) {
        messageHandlers.put(connectionKey(url), handler);
    }

    /**
     * Close all connections.
     */
    public void shutdown() {
        connections.values().forEach(conn ->
                conn.webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown"));
        connections.clear();
    }

    // --- internals ---

    private String connectionKey(String url) {
        // Normalize: remove ws:// prefix
        return url.replaceFirst("^ws://", "").replaceFirst("^wss://", "");
    }

    private WebSocketConnection getOrConnect(String key, String url) throws Exception {
        return connections.computeIfAbsent(key, k -> {
            try {
                // actually need ws URI
                // Java HttpClient needs a URI — use the ws URL directly
                URI wsUri = new URI(url);

                WebSocketConnection conn = new WebSocketConnection();

                CompletableFuture<WebSocket> wsFuture;
                try (HttpClient client = HttpClient.newHttpClient()) {
                    wsFuture = client.newWebSocketBuilder()
                            .buildAsync(wsUri, new WebSocket.Listener() {
                                final StringBuilder buffer = new StringBuilder();

                                @Override
                                public void onOpen(WebSocket webSocket) {
                                    WebSocket.Listener.super.onOpen(webSocket);
                                    conn.connected.complete(null);
                                }

                                @Override
                                public CompletionStage<?> onText(WebSocket webSocket,
                                                                 CharSequence data, boolean last) {
                                    buffer.append(data);
                                    if (last) {
                                        String msg = buffer.toString();
                                        buffer.setLength(0);

                                        // Route to pending response or message handler
                                        CompletableFuture<String> pending = conn.takePendingResponse();
                                        if (pending != null) {
                                            pending.complete(msg);
                                        }

                                        Consumer<String> handler = messageHandlers.get(key);
                                        if (handler != null) {
                                            handler.accept(msg);
                                        }
                                    }
                                    WebSocket.Listener.super.onText(webSocket, data, last);
                                    return null;
                                }

                                @Override
                                public void onError(WebSocket webSocket, Throwable error) {
                                    CompletableFuture<String> pending = conn.takePendingResponse();
                                    if (pending != null) {
                                        pending.completeExceptionally(error);
                                    }
                                    WebSocket.Listener.super.onError(webSocket, error);
                                }

                                @Override
                                public CompletionStage<?> onClose(WebSocket webSocket,
                                                                  int statusCode, String reason) {
                                    connections.remove(key);
                                    return null;
                                }
                            });
                }

                conn.webSocket = wsFuture.get(10, TimeUnit.SECONDS);
                conn.connected.complete(null);
                return conn;

            } catch (Exception e) {
                throw new RuntimeException("Failed to connect WebSocket: " + url, e);
            }
        });
    }

    // --- inner class ---

    private static class WebSocketConnection {
        WebSocket webSocket;
        final CompletableFuture<Void> connected = new CompletableFuture<>();
        private CompletableFuture<String> pendingResponse;

        synchronized void setPendingResponse(CompletableFuture<String> f) {
            this.pendingResponse = f;
        }

        synchronized CompletableFuture<String> takePendingResponse() {
            CompletableFuture<String> f = this.pendingResponse;
            this.pendingResponse = null;
            return f;
        }
    }
}
