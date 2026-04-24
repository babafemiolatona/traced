package com.tech.traced.service;

import com.tech.traced.models.Span;
import com.tech.traced.models.Trace;
import com.tech.traced.repository.TraceRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SpanIngestService {

    private static final Logger logger = LoggerFactory.getLogger(SpanIngestService.class);

    private final TraceRepository traceRepository;

    @Value("${window.minutes:30}")
    private int windowMinutes;

    public void ingestBatch(List<Span> batch) {
        if (batch == null || batch.isEmpty()) {
            logger.warn("Received empty batch, skipping");
            return;
        }

        Instant cutoff = Instant.now().minusSeconds(windowMinutes * 60L);
        List<Span> validSpans = batch.stream()
                .filter(span -> {
                    if (span.getStartTime() != null && span.getStartTime().isBefore(cutoff)) {
                        logger.warn("Span outside window, rejecting: spanId={}, traceId={}, age={}ms",
                                span.getSpanId(), span.getTraceId(),
                                Instant.now().toEpochMilli() - span.getStartTime().toEpochMilli());
                        return false;
                    }
                    return true;
                })
                .toList();

        if (validSpans.isEmpty()) {
            logger.warn("All spans in batch are outside the rolling window");
            return;
        }

        int addedCount = 0;
        for (Span span : validSpans) {
            if (!validateSpan(span)) {
                logger.warn("Invalid span in batch, skipping: {}", span.getSpanId());
                continue;
            }

            traceRepository.addSpan(span);
            addedCount++;
        }

        int orphanCount = traceRepository.getOrphanCount();
        int traceCount = traceRepository.getTraceCount();
        logger.info("Batch ingested: {} spans added, {} traces total, {} orphans waiting",
                addedCount, traceCount, orphanCount);
    }

    public boolean validateSpan(Span span) {
        if (span == null) {
            logger.warn("Null span");
            return false;
        }
        if (span.getSpanId() == null || span.getSpanId().isEmpty()) {
            logger.warn("Missing span_id");
            return false;
        }
        if (span.getTraceId() == null || span.getTraceId().isEmpty()) {
            logger.warn("Missing trace_id for spanId={}", span.getSpanId());
            return false;
        }
        if (span.getServiceName() == null || span.getServiceName().isEmpty()) {
            logger.warn("Missing service_name for spanId={}", span.getSpanId());
            return false;
        }
        if (span.getStartTime() == null) {
            logger.warn("Missing start_time for spanId={}", span.getSpanId());
            return false;
        }
        if (span.getDuration() == null || span.getDuration() < 0) {
            logger.warn("Invalid duration for spanId={}: {}", span.getSpanId(), span.getDuration());
            return false;
        }
        return true;
    }

    public Optional<Trace> getTrace(String traceId) {
        return traceRepository.getTrace(traceId);
    }

    public List<Trace> getAllTraces() {
        return traceRepository.getAllTraces();
    }

    public int getTraceCount() {
        return traceRepository.getTraceCount();
    }

    public int getOrphanCount() {
        return traceRepository.getOrphanCount();
    }

    public int getSpanCountForTrace(String traceId) {
        return traceRepository.getSpanCountForTrace(traceId);
    }
}