package com.feign.processor;

import com.feign.framework.annotations.*;
import com.feign.framework.codec.Encoder;
import com.feign.framework.http.HttpMethod;
import com.feign.framework.http.Request;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Builds a Request from @FeignMethod + method parameters.
 */
class RequestBuilder {

    private final Encoder encoder;

    RequestBuilder(Encoder encoder) { this.encoder = encoder; }

    /** Build path, query params, headers, and body — no base URL yet. */
    Result build(FeignMethod methodAnn, Method method, Object[] args) throws Exception {
        String path = resolvePath(methodAnn, method, args);
        Map<String, String> queryParams = extractQueryParams(method, args);
        Map<String, String> headers = extractAllHeaders(methodAnn, method, args);
        byte[] body = encodeBody(methodAnn, method, args);

        // URL-encode query param values
        Map<String, String> encodedQuery = new LinkedHashMap<>();
        queryParams.forEach((k, v) -> encodedQuery.put(k, URLEncoder.encode(v, StandardCharsets.UTF_8)));

        return new Result(path, encodedQuery, headers, body);
    }

    /** Assemble full URL from base + path + query. */
    static String assembleUrl(String baseUrl, String path, Map<String, String> queryParams) {
        if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        if (path.startsWith("/"))  path = path.substring(1);
        String url = baseUrl + "/" + path;
        if (!queryParams.isEmpty()) {
            url += "?" + queryParams.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .reduce((a, b) -> a + "&" + b).orElse("");
        }
        return url;
    }

    record Result(String path, Map<String, String> queryParams,
                  Map<String, String> headers, byte[] body) {}

    // ── path ──

    private String resolvePath(FeignMethod m, Method method, Object[] args) {
        String[] segs = m.path();
        if (segs.length == 0) return method.getName();
        String path = String.join("/", segs);
        Parameter[] params = method.getParameters();
        for (int i = 0; i < params.length; i++) {
            Path p = params[i].getAnnotation(Path.class);
            if (p != null && args != null && i < args.length && args[i] != null)
                path = path.replace("{" + p.value() + "}", args[i].toString());
        }
        return path;
    }

    // ── query ──

    private Map<String, String> extractQueryParams(Method method, Object[] args) {
        Map<String, String> map = new LinkedHashMap<>();
        if (args == null) return map;
        Parameter[] params = method.getParameters();
        for (int i = 0; i < params.length; i++) {
            Query q = params[i].getAnnotation(Query.class);
            if (q != null && args[i] != null) map.put(q.value(), args[i].toString());
        }
        return map;
    }

    // ── headers ──

    private Map<String, String> extractAllHeaders(FeignMethod methodAnn, Method method, Object[] args) {
        Map<String, String> headers = new LinkedHashMap<>();
        // static headers from @FeignMethod
        for (String h : methodAnn.headers()) {
            int c = h.indexOf(':');
            if (c > 0) headers.put(h.substring(0, c).trim(), h.substring(c + 1).trim());
        }
        // dynamic headers from @Header params
        if (args != null) {
            Parameter[] params = method.getParameters();
            for (int i = 0; i < params.length; i++) {
                Header h = params[i].getAnnotation(Header.class);
                if (h != null && args[i] != null) headers.put(h.value(), args[i].toString());
            }
        }
        return headers;
    }

    // ── body ──

    private byte[] encodeBody(FeignMethod ann, Method method, Object[] args) throws Exception {
        if (args == null) return null;

        Parameter[] params = method.getParameters();
        String contentType = ann.contentType();

        // Form-encoded
        if ("application/x-www-form-urlencoded".equals(contentType)) {
            return encodeFormBody(params, args);
        }

        // @Body explicit
        for (int i = 0; i < params.length; i++) {
            Body body = params[i].getAnnotation(Body.class);
            if (body != null && args[i] != null) {
                if (body.raw()) return args[i].toString().getBytes();
                return encoder.encode(args[i], params[i].getParameterizedType());
            }
        }

        // Implicit: first non-primitive, non-@Path, non-@Query param
        for (int i = 0; i < params.length; i++) {
            if (params[i].getAnnotation(Path.class) != null) continue;
            if (params[i].getAnnotation(Query.class) != null) continue;
            if (params[i].getAnnotation(Header.class) != null) continue;
            if (params[i].getAnnotation(Body.class) != null) continue;
            if (args[i] != null && !isPrimitiveOrWrapper(params[i].getType())) {
                return encoder.encode(args[i], params[i].getParameterizedType());
            }
        }
        return null;
    }

    private byte[] encodeFormBody(Parameter[] params, Object[] args) {
        Map<String, String> form = new LinkedHashMap<>();
        for (int i = 0; i < params.length; i++) {
            if (params[i].getAnnotation(Path.class) != null) continue;
            if (args[i] == null) continue;
            String key = params[i].getAnnotation(Query.class) != null
                    ? params[i].getAnnotation(Query.class).value() : params[i].getName();
            form.put(key, args[i].toString());
        }
        if (form.isEmpty()) return null;
        return form.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .reduce((a, b) -> a + "&" + b).orElse("").getBytes();
    }

    private boolean isPrimitiveOrWrapper(Class<?> t) {
        return t.isPrimitive() || t == String.class
                || Number.class.isAssignableFrom(t) || Boolean.class.isAssignableFrom(t);
    }
}
