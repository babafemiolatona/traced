package com.tech.traced.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tech.traced.models.Trace;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TraceResponse {

    @JsonProperty("traceId")
    private String traceId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("spanCount")
    private int spanCount;

    @JsonProperty("duration")
    private long duration; // milliseconds

    @JsonProperty("firstSpanTime")
    private Instant firstSpanTime;

    @JsonProperty("lastSpanTime")
    private Instant lastSpanTime;

    @JsonProperty("spans")
    private List<SpanResponse> spans;

    public static TraceResponse fromTrace(Trace trace) {
        return new TraceResponse(
                trace.getTraceId(),
                trace.getStatus(),
                trace.getSpanCount(),
                trace.getDurationMs(),
                trace.getFirstSpanTime(),
                trace.getLastSpanTime(),
                trace.getSpans().stream()
                        .map(SpanResponse::fromSpan)
                        .collect(Collectors.toList())
        );
    }

    public static TraceResponse summarizeTrace(Trace trace) {
        TraceResponse response = new TraceResponse();
        response.setTraceId(trace.getTraceId());
        response.setStatus(trace.getStatus());
        response.setSpanCount(trace.getSpanCount());
        response.setDuration(trace.getDurationMs());
        response.setFirstSpanTime(trace.getFirstSpanTime());
        response.setLastSpanTime(trace.getLastSpanTime());
        response.setSpans(null);
        return response;
    }
}