package com.feign.framework.trace;

/**
 * Represents a single span in a distributed trace.
 */
public class Span implements AutoCloseable {

    private final Tracer tracer;
    private final String traceId;
    private final String spanId;
    private final String parentSpanId;
    private final String name;
    private final boolean sampled;
    private final long startNanos;
    private long endNanos;
    private String remoteService;

    Span(Tracer tracer, String traceId, String spanId, String parentSpanId, String name, boolean sampled) {
        this.tracer = tracer;
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.name = name;
        this.sampled = sampled;
        this.startNanos = System.nanoTime();
    }

    /** Tag the remote service name this span calls. */
    public Span remoteService(String svc) { this.remoteService = svc; return this; }

    /** Complete the span and report. */
    public void finish() {
        this.endNanos = System.nanoTime();
        if (sampled) tracer.report(this);
    }

    @Override public void close() { finish(); }

    // ── getters ──

    public String traceId()       { return traceId; }
    public String spanId()        { return spanId; }
    public String parentSpanId()  { return parentSpanId; }
    public String name()          { return name; }
    public String remoteService() { return remoteService; }
    public boolean sampled()      { return sampled; }
    public long durationMicros()  { return (endNanos - startNanos) / 1000; }

    /** Extract a TraceContext from this span for downstream propagation. */
    public TraceContext toContext() {
        return new TraceContext(traceId, spanId, parentSpanId, sampled, java.util.Map.of());
    }

    @Override
    public String toString() {
        return String.format("[trace=%s span=%s parent=%s] %s%s — %dμs",
            traceId, spanId, parentSpanId, name,
            remoteService != null ? " → " + remoteService : "",
            durationMicros());
    }
}
