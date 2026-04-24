package com.tech.traced.models;

import lombok.*;
import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonProperty;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class Span {
    
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

    public boolean isRootSpan() {
        return parentSpanId == null || parentSpanId.isEmpty();
    }

    public boolean isError() {
        return "error".equalsIgnoreCase(status);
    }
}
