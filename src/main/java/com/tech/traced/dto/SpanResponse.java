package com.tech.traced.dto;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import com.tech.traced.models.Span;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SpanResponse {

    @JsonProperty("span_id")
    private String spanId;

    @JsonProperty("trace_id")
    private String traceId;

    @JsonProperty("parent_span_id")
    private String parentSpanId;

    @JsonProperty("service_name")
    private String serviceName;

    @JsonProperty("status")
    private String status;

    @JsonProperty("start_time")
    private Instant startTime;

    @JsonProperty("duration")
    private Long duration;

    public static SpanResponse fromSpan(Span span) {
        return new SpanResponse(
                span.getSpanId(),
                span.getTraceId(),
                span.getParentSpanId(),
                span.getServiceName(),
                span.getStatus(),
                span.getStartTime(),
                span.getDuration()
        );
    }
}