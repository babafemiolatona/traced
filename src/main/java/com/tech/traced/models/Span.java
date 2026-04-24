package com.tech.traced.models;

import lombok.*;
import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonProperty;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class Span {
    
    @JsonProperty("spanId")
    private String spanId;

    @JsonProperty("traceId")
    private String traceId;

    @JsonProperty("parentSpanId")
    private String parentSpanId;

    @JsonProperty("serviceName")
    private String serviceName;

    @JsonProperty("status")
    private String status;

    @JsonProperty("startTime")
    private Instant startTime;

    @JsonProperty("duration")
    private long duration;

    public boolean isRootSpan() {
        return parentSpanId == null || parentSpanId.isEmpty();
    }

    public boolean isError() {
        return "error".equalsIgnoreCase(status);
    }
}
