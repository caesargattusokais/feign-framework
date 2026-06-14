package com.feign.framework.trace;

/**
 * Configuration for tracing.
 *
 * <p>Spring Boot example:
 * <pre>{@code
 * feign.client.config.default.tracing.enabled=true
 * feign.client.config.default.tracing.service-name=my-app
 * feign.client.config.default.tracing.sample-rate=0.1
 * feign.client.config.default.tracing.header-trace-id=X-Trace-Id
 * feign.client.config.default.tracing.header-span-id=X-Span-Id
 * feign.client.config.default.tracing.reporter=logging
 * feign.client.config.default.tracing.log-enabled=true
 * }</pre>
 */
public class TracingConfig {

    private boolean enabled = true;
    private String serviceName = "unknown";
    private String headerTraceId = "X-Trace-Id";
    private String headerSpanId = "X-Span-Id";
    private String headerSampled = "X-Sampled";
    private String headerParentSpanId = "X-Parent-Span-Id";
    private double sampleRate = 1.0;       // 0.0 = none, 1.0 = all
    private String reporter = "logging";    // "logging" | "noop" | bean-name
    private boolean logEnabled = false;
    private int order = 0;

    // ── getters / setters / fluent ──

    public boolean isEnabled() { return enabled; }
    public TracingConfig setEnabled(boolean v) { this.enabled = v; return this; }

    public String getServiceName() { return serviceName; }
    public TracingConfig setServiceName(String v) { this.serviceName = v; return this; }

    public String getHeaderTraceId() { return headerTraceId; }
    public TracingConfig setHeaderTraceId(String v) { this.headerTraceId = v; return this; }

    public String getHeaderSpanId() { return headerSpanId; }
    public TracingConfig setHeaderSpanId(String v) { this.headerSpanId = v; return this; }

    public String getHeaderSampled() { return headerSampled; }
    public TracingConfig setHeaderSampled(String v) { this.headerSampled = v; return this; }

    public String getHeaderParentSpanId() { return headerParentSpanId; }
    public TracingConfig setHeaderParentSpanId(String v) { this.headerParentSpanId = v; return this; }

    public double getSampleRate() { return sampleRate; }
    public TracingConfig setSampleRate(double v) { this.sampleRate = v; return this; }

    public String getReporter() { return reporter; }
    public TracingConfig setReporter(String v) { this.reporter = v; return this; }

    public boolean isLogEnabled() { return logEnabled; }
    public TracingConfig setLogEnabled(boolean v) { this.logEnabled = v; return this; }

    public int getOrder() { return order; }
    public TracingConfig setOrder(int v) { this.order = v; return this; }
}
