package com.tech.traced.controller;

import java.util.List;

import com.tech.traced.service.SpanIngestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tech.traced.dto.SpanBatch;
import com.tech.traced.dto.TraceResponse;
import com.tech.traced.models.Span;
import com.tech.traced.models.Trace;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class SpanController {

    private final SpanIngestService ingestService;

    @PostMapping("/spans")
    public ResponseEntity<Void> ingestSpans(@Valid @RequestBody SpanBatch batch) {
        
        long startTime = System.currentTimeMillis();
        
        log.info("Received span batch with {} spans", batch.getSpans().size());
        
        List<Span> spans = new java.util.ArrayList<>();
        for (var spanRequest : batch.getSpans()) {
            long duration = spanRequest.getDuration() != null ? spanRequest.getDuration() : 0L;
            Span span = new Span(
                    spanRequest.getSpanId(),
                    spanRequest.getTraceId(),
                    spanRequest.getParentSpanId(),
                    spanRequest.getServiceName(),
                    spanRequest.getStatus(),
                    spanRequest.getStartTime(),
                    duration
            );
            spans.add(span);
        }
        
        ingestService.ingestBatch(spans);
        
        long duration = System.currentTimeMillis() - startTime;
        log.info("Span batch processed in {}ms", duration);
        
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/traces")
    public ResponseEntity<List<TraceResponse>> getAllTraces() {
        long startTime = System.currentTimeMillis();
        
        List<Trace> traces = ingestService.getAllTraces();
        List<TraceResponse> responses = traces.stream()
                .map(TraceResponse::fromTrace)
                .toList();
        
        long duration = System.currentTimeMillis() - startTime;
        log.info("Retrieved {} traces in {}ms", responses.size(), duration);
        
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/traces/{traceId}")
    public ResponseEntity<TraceResponse> getTrace(@PathVariable String traceId) {
        log.info("Received request for traceId={}", traceId);

        return ingestService.getTrace(traceId)
                .map(TraceResponse::fromTrace)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/stats")
    public ResponseEntity<StatsResponse> getStats() {
        return ResponseEntity.ok(new StatsResponse(
                ingestService.getTraceCount(),
                ingestService.getOrphanCount()
        ));
    }

    public record StatsResponse(int totalTraces, int orphanSpans) {}
}