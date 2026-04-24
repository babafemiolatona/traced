package com.tech.traced.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class SpanRequest {

    @NotBlank(message = "span_id is required")
    @JsonProperty("span_id")
    private String spanId;

    @NotBlank(message = "trace_id is required")
    @JsonProperty("trace_id")
    private String traceId;

    @JsonProperty("parent_span_id")
    private String parentSpanId;

    @NotBlank(message = "service_name is required")
    @JsonProperty("service_name")
    private String serviceName;

    @JsonProperty("status")
    private String status = "ok";

    @JsonProperty("start_time")
    @NotNull(message = "start_time is required")
    private long startTime;

    @NotNull(message = "duration is required")
    @JsonProperty("duration")
    private Long duration;

    public boolean isRootSpan() {
        return parentSpanId == null || parentSpanId.isEmpty();
    }

    public boolean isError() {
        return "error".equalsIgnoreCase(status);
    }
}