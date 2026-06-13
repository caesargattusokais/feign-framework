package examples.server;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * WebSocket 服务端示例 — 正确处理 PING/PONG 保活。
 *
 * <h3>保活要求（RFC 6455 §5.5.2）</h3>
 * <ul>
 *   <li>收到 PING (opcode=0x9) → 必须用相同的 payload 回复 PONG (opcode=0xA)</li>
 *   <li>可以主动发 PING 检测客户端是否存活</li>
 *   <li>CLOSE (opcode=0x8) → 优雅关闭</li>
 * </ul>
 *
 * <h3>客户端配置应对齐</h3>
 * 客户端: pingInterval=20s, pongTimeout=5s, idleTimeout=120s
 * 服务端: pingInterval=25s, pongTimeout=10s
 *
 * <p>启动后使用: ws://localhost:8080/chat
 */
public class WebSocketServerExample {

    private static final Logger log = Logger.getLogger(WebSocketServerExample.class.getName());

    // 连接集合
    private static final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private static final AtomicInteger sessionId = new AtomicInteger(0);

    // WebSocket 帧定义 (RFC 6455)
    private static final int OP_TEXT   = 0x1;
    private static final int OP_CLOSE  = 0x8;
    private static final int OP_PING   = 0x9;
    private static final int OP_PONG   = 0xA;

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/chat", exchange -> {
            // WebSocket 升级握手
            String key = exchange.getRequestHeaders().getFirst("Sec-WebSocket-Key");
            if (key == null) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }

            String acceptKey = generateAcceptKey(key);
            exchange.getResponseHeaders().set("Upgrade", "websocket");
            exchange.getResponseHeaders().set("Connection", "Upgrade");
            exchange.getResponseHeaders().set("Sec-WebSocket-Accept", acceptKey);
            exchange.sendResponseHeaders(101, -1);

            // 升级完成 — 开始 WebSocket 帧通信
            String id = "ws-" + sessionId.incrementAndGet();
            WebSocketSession session = new WebSocketSession(id, exchange);
            sessions.put(id, session);
            log.info("WebSocket connected: " + id);

            // ── 心跳线程：服务端主动发 PING ──
            session.heartbeat = Executors.newSingleThreadScheduledExecutor();
            session.heartbeat.scheduleAtFixedRate(() -> {
                if (!session.open) {
                    session.heartbeat.shutdown();
                    return;
                }
                // 发送 PING
                try {
                    sendFrame(session, OP_PING, "ping".getBytes());
                    session.pingSent = System.currentTimeMillis();
                } catch (IOException e) {
                    log.warning("PING failed for " + id + ": " + e.getMessage());
                    closeSession(id);
                }

                // 检查上次 PONG 是否超时
                if (System.currentTimeMillis() - session.lastPong > 10_000) {
                    log.warning("PONG timeout for " + id);
                    closeSession(id);
                }
            }, 25, 25, TimeUnit.SECONDS); // 每 25s 发一次 PING

            // ── 读帧循环 ──
            try {
                readFrames(id, session, exchange);
            } catch (IOException e) {
                log.warning("Connection error " + id + ": " + e.getMessage());
            } finally {
                closeSession(id);
            }
        });

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        log.info("WebSocket server started on ws://localhost:8080/chat");
        log.info("  Keep-alive: PING every 25s, PONG timeout 10s");
        log.info("  正确处理 PING/PONG (RFC 6455 §5.5.2)");
    }

    // ── 帧读写 ──

    private static void readFrames(String id, WebSocketSession session, HttpExchange exchange) throws IOException {
        var in = exchange.getRequestBody();

        while (session.open) {
            int b0 = in.read();
            if (b0 == -1) break;

            boolean fin = (b0 & 0x80) != 0;
            int opcode = b0 & 0x0F;

            int b1 = in.read();
            if (b1 == -1) break;

            boolean masked = (b1 & 0x80) != 0;
            long len = b1 & 0x7F;

            if (len == 126) {
                len = ((in.read() & 0xFF) << 8) | (in.read() & 0xFF);
            } else if (len == 127) {
                len = 0;
                for (int i = 0; i < 8; i++) {
                    len = (len << 8) | (in.read() & 0xFF);
                }
            }

            byte[] mask = new byte[4];
            if (masked) {
                in.readNBytes(mask, 0, 4);
            }

            byte[] payload = new byte[(int) len];
            in.readNBytes(payload, 0, (int) len);

            if (masked) {
                for (int i = 0; i < payload.length; i++) {
                    payload[i] ^= mask[i & 3];
                }
            }

            switch (opcode) {
                case OP_TEXT:
                    String msg = new String(payload);
                    log.info("[" + id + "] TEXT: " + msg);

                    // 广播给所有连接
                    for (WebSocketSession s : sessions.values()) {
                        if (s.open) {
                            sendFrame(s, OP_TEXT, ("[" + id + "]: " + msg).getBytes());
                        }
                    }
                    break;

                case OP_PING:
                    // ── RFC 6455: 必须回复 PONG，payload 应与 PING 一致 ──
                    session.lastPong = System.currentTimeMillis();
                    sendFrame(session, OP_PONG, payload);
                    log.fine("[" + id + "] PING → PONG");
                    break;

                case OP_PONG:
                    // 客户端回复的 PONG（响应服务端的 PING）
                    session.lastPong = System.currentTimeMillis();
                    log.fine("[" + id + "] PONG received");
                    break;

                case OP_CLOSE:
                    log.info("[" + id + "] CLOSE");
                    sendFrame(session, OP_CLOSE, new byte[0]);
                    closeSession(id);
                    return;

                default:
                    log.warning("[" + id + "] Unknown opcode: " + opcode);
            }
        }
    }

    private static void sendFrame(WebSocketSession session, int opcode, byte[] payload) throws IOException {
        synchronized (session) {
            OutputStream out = session.exchange.getResponseBody();

            // FIN + opcode
            out.write(0x80 | opcode);

            // Payload length + mask
            int len = payload.length;
            if (len < 126) {
                out.write(len);
            } else if (len < 65536) {
                out.write(126);
                out.write((len >> 8) & 0xFF);
                out.write(len & 0xFF);
            } else {
                out.write(127);
                for (int i = 7; i >= 0; i--) {
                    out.write((int)((len >> (i * 8)) & 0xFF));
                }
            }

            out.write(payload);
            out.flush();
        }
    }

    private static void closeSession(String id) {
        WebSocketSession session = sessions.remove(id);
        if (session != null) {
            session.open = false;
            if (session.heartbeat != null) {
                session.heartbeat.shutdownNow();
            }
            log.info("WebSocket disconnected: " + id);
        }
    }

    // ── WebSocket 握手 ──

    private static String generateAcceptKey(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes());
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // ── session ──

    static class WebSocketSession {
        final String id;
        final HttpExchange exchange;
        volatile boolean open = true;
        volatile long lastPong = System.currentTimeMillis();
        volatile long pingSent;
        ScheduledExecutorService heartbeat;

        WebSocketSession(String id, HttpExchange exchange) {
            this.id = id;
            this.exchange = exchange;
        }
    }
}
