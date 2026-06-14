package com.feign.framework.trace;

import com.feign.framework.FeignException;
import com.feign.framework.Response;
import com.feign.framework.http.Request;
import com.feign.framework.interceptor.FeignInterceptor;

/**
 * Feign interceptor that propagates trace context via HTTP headers.
 *
 * <h3>Usage (manual)</h3>
 * <pre>{@code
 * Tracer tracer = Tracer.builder().serviceName("my-app")
 *     .reporter(Tracer.loggingReporter()).build();
 * new FeignClientFactory()
 *     .addInterceptor(new TracingInterceptor(tracer))
 *     .build(UserService.class);
 * }</pre>
 *
 * <h3>Usage (Spring Boot)</h3>
 * <pre>{@code
 * feign:
 *   client:
 *     config:
 *       default:
 *         tracing:
 *           enabled: true
 *           service-name: my-app
 *           sample-rate: 0.1
 *           header-trace-id: X-Trace-Id
 *           header-span-id: X-Span-Id
 *           reporter: logging
 * }</pre>
 *
 * <h3>Header propagation flow</h3>
 * <pre>
 * Caller                           Callee
 *   span = tracer.newSpan("call")
 *   inject: X-Trace-Id: abc123    →  extract: TraceContext from headers
 *           X-Span-Id: def456          span = tracer.newChildSpan(ctx, "handle")
 *   response received ←               span.finish()
 *   inject: X-Span-Id: def456
 *   span.finish()
 * </pre>
 */
public class TracingInterceptor implements FeignInterceptor {

    private final Tracer tracer;
    private final TracingConfig config;
    private static final ThreadLocal<Span> currentSpan = new ThreadLocal<>();

    public TracingInterceptor(Tracer tracer) {
        this(tracer, new TracingConfig());
    }

    public TracingInterceptor(Tracer tracer, TracingConfig config) {
        this.tracer = tracer;
        this.config = config;
    }

    @Override
    public int order() { return config.getOrder(); }

    @Override
    public Request beforeExecute(Request request) {
        if (!config.isEnabled()) return request;

        // Create span for this outgoing call
        Span span;
        TraceContext parent = extractParent(request);
        if (parent != null) {
            span = tracer.newChildSpan(parent, "feign:" + request.getMethod().name());
        } else {
            span = tracer.newSpan("feign:" + request.getMethod().name());
        }
        span.remoteService(serviceNameFromUrl(request.getUrl()));

        // Inject trace headers
        request.getHeaders().put(tracer.headerTraceId(), span.traceId());
        request.getHeaders().put(tracer.headerSpanId(), span.spanId());
        if (span.parentSpanId() != null) {
            request.getHeaders().put(tracer.headerParentSpanId(), span.parentSpanId());
        }
        request.getHeaders().put(tracer.headerSampled(), String.valueOf(span.sampled()));

        currentSpan.set(span);

        if (config.isLogEnabled()) {
            System.out.println("[TRACE] " + span.name() + " → " + span.remoteService());
        }

        return request;
    }

    @Override
    public Response afterExecute(Response response) {
        Span span = currentSpan.get();
        if (span != null) {
            span.finish();
            currentSpan.remove();
        }
        return response;
    }

    @Override
    public void onError(Request request, FeignException exception) {
        Span span = currentSpan.get();
        if (span != null) {
            span.finish();
            currentSpan.remove();
        }
    }

    // ── parent extraction from incoming request headers ──

    private TraceContext extractParent(Request request) {
        String traceId = request.getHeaders().get(tracer.headerTraceId());
        if (traceId == null || traceId.isEmpty()) return null;

        String spanId = request.getHeaders().get(tracer.headerSpanId());
        String parentSpanId = request.getHeaders().get(tracer.headerParentSpanId());
        String sampledStr = request.getHeaders().get(tracer.headerSampled());
        boolean sampled = sampledStr == null || !"false".equals(sampledStr);

        return new TraceContext(traceId,
            spanId != null ? spanId : "unknown",
            parentSpanId, sampled, java.util.Map.of());
    }

    private String serviceNameFromUrl(String url) {
        try {
            String host = url.replaceFirst("^\\w+://", "").split("[/:]")[0];
            return host;
        } catch (Exception e) {
            return "unknown";
        }
    }
}
