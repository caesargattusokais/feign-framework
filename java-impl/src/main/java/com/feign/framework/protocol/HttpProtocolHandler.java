package com.feign.framework.protocol;

import com.feign.framework.FeignException;
import com.feign.framework.Response;
import com.feign.framework.http.Request;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpPatch;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * HTTP/HTTPS protocol handler with connection pooling.
 *
 * <p>Connection pool configuration:
 * <pre>{@code
 * HttpProtocolHandler handler = new HttpProtocolHandler(
 *     5000,   // connectTimeout ms
 *     10000,  // readTimeout ms
 *     200,    // maxTotal connections
 *     20      // maxPerRoute connections
 * );
 * }</pre>
 */
public class HttpProtocolHandler implements ProtocolHandler {

    private final CloseableHttpClient httpClient;
    private final int connectTimeout;
    private final int readTimeout;

    // ── constructors ──

    /** Default: 200 max connections, 20 per route */
    public HttpProtocolHandler(int connectTimeout, int readTimeout) {
        this(connectTimeout, readTimeout, 200, 20);
    }

    /** Full control over connection pool */
    public HttpProtocolHandler(int connectTimeout, int readTimeout,
                                int maxTotal, int maxPerRoute) {
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;

        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(maxTotal);
        cm.setDefaultMaxPerRoute(maxPerRoute);

        this.httpClient = HttpClientBuilder.create()
                .setConnectionManager(cm)
                .build();
    }

    /** Inject a pre-configured HttpClient (for advanced users) */
    public HttpProtocolHandler(CloseableHttpClient httpClient,
                                int connectTimeout, int readTimeout) {
        this.httpClient = httpClient;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    public HttpProtocolHandler() {
        this(5000, 5000);
    }

    // ── ProtocolHandler ──

    @Override
    public String scheme() {
        return "http";
    }

    @Override
    public Response execute(Request request) throws Exception {
        try (CloseableHttpResponse resp = dispatch(request)) {
            return parseResponse(request.getUrl(), resp);
        }
    }

    @Override
    public CompletableFuture<Response> executeAsync(Request request) {
        return CompletableFuture.supplyAsync(() -> {
            try (CloseableHttpResponse resp = dispatch(request)) {
                return parseResponse(request.getUrl(), resp);
            } catch (Exception e) {
                throw new CompletionException(
                    new FeignException("Async request failed: " + request.getUrl(), e));
            }
        });
    }

    @Override
    public boolean isAvailable(String url) {
        try {
            HttpGet head = new HttpGet(url);
            try (CloseableHttpResponse resp = httpClient.execute(head)) {
                int code = resp.getCode();
                return code >= 200 && code < 400;
            }
        } catch (Exception e) {
            return false;
        }
    }

    // ── dispatch ──

    private CloseableHttpResponse dispatch(Request request) throws IOException {
        return switch (request.getMethod()) {
            case GET    -> doGet(request);
            case POST   -> doPost(request);
            case PUT    -> doPut(request);
            case DELETE -> doDelete(request);
            case PATCH  -> doPatch(request);
            default -> throw new FeignException("Unsupported HTTP method: " + request.getMethod());
        };
    }

    private CloseableHttpResponse doGet(Request req) throws IOException {
        HttpGet h = new HttpGet(req.getUrl());
        applyHeaders(h, req.getHeaders());
        return httpClient.execute(h);
    }

    private CloseableHttpResponse doPost(Request req) throws IOException {
        HttpPost h = new HttpPost(req.getUrl());
        applyHeaders(h, req.getHeaders());
        applyBody(h, req);
        return httpClient.execute(h);
    }

    private CloseableHttpResponse doPut(Request req) throws IOException {
        HttpPut h = new HttpPut(req.getUrl());
        applyHeaders(h, req.getHeaders());
        applyBody(h, req);
        return httpClient.execute(h);
    }

    private CloseableHttpResponse doDelete(Request req) throws IOException {
        HttpDelete h = new HttpDelete(req.getUrl());
        applyHeaders(h, req.getHeaders());
        return httpClient.execute(h);
    }

    private CloseableHttpResponse doPatch(Request req) throws IOException {
        HttpPatch h = new HttpPatch(req.getUrl());
        applyHeaders(h, req.getHeaders());
        applyBody(h, req);
        return httpClient.execute(h);
    }

    // ── helpers ──

    private void applyHeaders(HttpUriRequestBase http, Map<String, String> headers) {
        if (headers != null) {
            headers.forEach(http::setHeader);
        }
    }

    private void applyBody(HttpUriRequestBase http, Request req) {
        byte[] body = req.getBody();
        if (body != null && body.length > 0) {
            http.setEntity(new ByteArrayEntity(body, ContentType.APPLICATION_JSON));
        }
    }

    private Response parseResponse(String url, CloseableHttpResponse resp) throws IOException {
        int status = resp.getCode();
        Map<String, String> headers = new HashMap<>();
        for (org.apache.hc.core5.http.Header h : resp.getHeaders()) {
            headers.put(h.getName(), h.getValue());
        }
        HttpEntity entity = resp.getEntity();
        String body = entity != null ? EntityUtils.toString(entity) : null;
        return Response.of(url, status, headers, body);
    }
}
