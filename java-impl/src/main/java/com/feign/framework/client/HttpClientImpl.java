package com.feign.framework.client;

import com.feign.framework.FeignException;
import com.feign.framework.http.Request;
import com.feign.framework.Response;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * HTTP client implementation using Apache HttpClient 5.2.1.
 * Supports synchronous and asynchronous requests.
 */
public class HttpClientImpl implements HttpClient {
    private final CloseableHttpClient httpClient;
    private final HttpClientConfig config;

    public HttpClientImpl(HttpClientConfig config) {
        this.config = config;
        this.httpClient = createHttpClient();
    }

    private CloseableHttpClient createHttpClient() {
        return HttpClients.custom()
                .build();
    }

    @Override
    public Response execute(Request request)  {
        try {
            CloseableHttpResponse httpResponse = executeHttpRequest(request);
            return parseResponse(httpResponse, request.getUrl());
        } catch (IOException e) {
            throw new FeignException("Request failed", e);
        }
    }

    @Override
    public CompletableFuture<Response> executeAsync(Request request)  {
        return CompletableFuture.supplyAsync(() -> {
            try {
                CloseableHttpResponse httpResponse = executeHttpRequest(request);
                return parseResponse(httpResponse, request.getUrl());
            } catch (IOException e) {
                throw new CompletionException(
                    new FeignException("Async request failed", e)
                );
            }
        });
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getName() {
        return "Apache-HttpClient-5";
    }

    private CloseableHttpResponse executeHttpRequest(Request request) throws IOException {
        return switch (request.getMethod()) {
            case GET -> executeGet(request);
            case POST -> executePost(request);
            default -> throw new FeignException("Unsupported HTTP method: " + request.getMethod());
        };
    }

    private CloseableHttpResponse executeGet(Request request) throws IOException {
        HttpGet httpGet = new HttpGet(request.getUrl());
        setHeaders(httpGet, request.getHeaders());
        return httpClient.execute(httpGet);
    }

    private CloseableHttpResponse executePost(Request request) throws IOException {
        HttpPost httpPost = new HttpPost(request.getUrl());
        setHeaders(httpPost, request.getHeaders());

        byte[] body = request.getBody();
        if (body != null && body.length > 0) {
            httpPost.setEntity(new org.apache.hc.core5.http.io.entity.ByteArrayEntity(
                    body, org.apache.hc.core5.http.ContentType.APPLICATION_JSON));
        }

        return httpClient.execute(httpPost);
    }

    private void setHeaders(HttpUriRequestBase httpRequest, Map<String, String> headers) {
        if (headers != null) {
            headers.forEach(httpRequest::setHeader);
        }
    }

    private Response parseResponse(CloseableHttpResponse httpResponse, String url) throws IOException {
        int statusCode = httpResponse.getCode();
        Map<String, String> headers = new HashMap<>();
        for (org.apache.hc.core5.http.Header header : httpResponse.getHeaders()) {
            headers.put(header.getName(), header.getValue());
        }

        HttpEntity entity = httpResponse.getEntity();
        String body = null;
        try {
            body = entity != null ? EntityUtils.toString(entity) : null;
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

        return Response.of(url, statusCode, headers, body);
    }
}
