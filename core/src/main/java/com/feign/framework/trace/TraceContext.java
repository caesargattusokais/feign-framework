package com.feign.framework.trace;

import java.util.Map;

/**
 * Holds trace identifiers for a single request.
 */
public class TraceContext {
    private final String traceId;
    private final String spanId;
    private final String parentSpanId;
    private final boolean sampled;
    private final Map<String, String> baggage;

    public TraceContext(String traceId, String spanId, String parentSpanId, boolean sampled, Map<String, String> baggage) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.sampled = sampled;
        this.baggage = baggage != null ? Map.copyOf(baggage) : Map.of();
    }

    public String traceId() { return traceId; }
    public String spanId() { return spanId; }
    public String parentSpanId() { return parentSpanId; }
    public boolean sampled() { return sampled; }
    public Map<String, String> baggage() { return baggage; }
}
