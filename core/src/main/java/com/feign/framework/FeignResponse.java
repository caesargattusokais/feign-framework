package com.feign.framework;

import java.util.Collections;
import java.util.Map;

/**
 * Typed response wrapper that carries both the decoded body and HTTP headers.
 *
 * <p>Use as return type to access response headers:
 * <pre>{@code
 * FeignResponse<User> get(@Path("id") Long id);
 *
 * FeignResponse<User> resp = service.get(1L);
 * User user = resp.getBody();
 * String location = resp.getHeader("Location");
 * }</pre>
 */
public class FeignResponse<T> {
    private final T body;
    private final Map<String, String> headers;

    public FeignResponse(T body, Map<String, String> headers) {
        this.body = body;
        this.headers = headers != null ? Collections.unmodifiableMap(headers) : Collections.emptyMap();
    }

    public T getBody() { return body; }
    public Map<String, String> getHeaders() { return headers; }

    public String getHeader(String name) {
        return headers.get(name);
    }

    public boolean hasHeader(String name) {
        return headers.containsKey(name);
    }
}
