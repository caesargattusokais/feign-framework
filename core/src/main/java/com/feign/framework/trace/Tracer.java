package com.feign.framework.trace;

import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Tracer — creates spans, manages trace context propagation.
 *
 * <h3>Configuration</h3>
 * <pre>{@code
 * Tracer tracer = Tracer.builder()
 *     .serviceName("my-service")
 *     .headerTraceId("X-Trace-Id")
 *     .headerSpanId("X-Span-Id")
 *     .sampleRate(1.0)   // 100% sampling
 *     .reporter(span -> log.info(span.toString()))  // custom reporter
 *     .build();
 * }</pre>
 *
 * <h3>Reporters</h3>
 * Built-in: {@code LoggingReporter}, {@code NoopReporter}.
 * External: Zipkin/Jaeger by implementing {@code Consumer<Span>}.
 */
public class Tracer {

    // ── config ──
    private final String serviceName;
    private final String headerTraceId;
    private final String headerSpanId;
    private final String headerSampled;
    private final String headerParentSpanId;
    private final double sampleRate;
    private final Consumer<Span> reporter;

    private Tracer(Builder b) {
        this.serviceName      = b.serviceName;
        this.headerTraceId    = b.headerTraceId;
        this.headerSpanId     = b.headerSpanId;
        this.headerSampled    = b.headerSampled;
        this.headerParentSpanId = b.headerParentSpanId;
        this.sampleRate       = b.sampleRate;
        this.reporter         = b.reporter;
    }

    // ── create span ──

    /** Create a new root span (no incoming trace). */
    public Span newSpan(String name) {
        boolean sampled = Math.random() < sampleRate;
        return new Span(this, traceId(), spanId(), null, name, sampled);
    }

    /** Create a child span from an incoming TraceContext (extracted from headers). */
    public Span newChildSpan(TraceContext parent, String name) {
        return new Span(this, parent.traceId(), spanId(), parent.spanId(), name, parent.sampled());
    }

    // ── header names ──

    public String headerTraceId()       { return headerTraceId; }
    public String headerSpanId()        { return headerSpanId; }
    public String headerSampled()       { return headerSampled; }
    public String headerParentSpanId()  { return headerParentSpanId; }
    public String serviceName()         { return serviceName; }

    // ── report ──

    void report(Span span) { reporter.accept(span); }

    // ── id generation ──

    private static String traceId() { return Long.toHexString(System.nanoTime()) + randomHex(8); }
    private static String spanId()  { return randomHex(16); }
    private static String randomHex(int len) {
        StringBuilder sb = new StringBuilder(len);
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < len; i++) sb.append(Integer.toHexString(r.nextInt(16)));
        return sb.toString();
    }

    // ── builder ──

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String serviceName = "unknown";
        private String headerTraceId = "X-Trace-Id";
        private String headerSpanId = "X-Span-Id";
        private String headerSampled = "X-Sampled";
        private String headerParentSpanId = "X-Parent-Span-Id";
        private double sampleRate = 1.0;
        private Consumer<Span> reporter = span -> {};

        public Builder serviceName(String v)     { this.serviceName = v; return this; }
        public Builder headerTraceId(String v)   { this.headerTraceId = v; return this; }
        public Builder headerSpanId(String v)    { this.headerSpanId = v; return this; }
        public Builder headerSampled(String v)   { this.headerSampled = v; return this; }
        public Builder headerParentSpanId(String v) { this.headerParentSpanId = v; return this; }
        public Builder sampleRate(double v)      { this.sampleRate = v; return this; }
        public Builder reporter(Consumer<Span> v) { this.reporter = v; return this; }
        public Tracer build() { return new Tracer(this); }
    }

    // ── built-in reporters ──

    public static Consumer<Span> loggingReporter() {
        Logger log = Logger.getLogger("feign.trace");
        return span -> log.info(span.toString());
    }

    public static Consumer<Span> noopReporter() { return span -> {}; }
}
