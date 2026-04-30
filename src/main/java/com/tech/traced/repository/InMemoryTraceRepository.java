package com.tech.traced.repository;

import com.tech.traced.models.Span;
import com.tech.traced.models.Trace;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Repository
public class InMemoryTraceRepository implements TraceRepository {

    private final ConcurrentHashMap<String, Trace> traces = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, List<Span>> orphans = new ConcurrentHashMap<>();

    @Override
    public void addSpan(Span span) {
        if (span == null || span.getTraceId() == null) {
            log.warn("Null span or traceId, skipping");
            return;
        }

        List<Span> traceOrphans = orphans.getOrDefault(span.getTraceId(), new ArrayList<>());
        boolean isOrphan = traceOrphans.stream()
                .anyMatch(s -> s.getSpanId().equals(span.getSpanId()));

        if (isOrphan) {
            traceOrphans.removeIf(s -> s.getSpanId().equals(span.getSpanId()));
            if (traceOrphans.isEmpty()) {
                orphans.remove(span.getTraceId());
            } else {
                orphans.put(span.getTraceId(), traceOrphans);
            }
        }

        if (span.isRootSpan()) {
            Trace trace = traces.computeIfAbsent(span.getTraceId(), k -> new Trace(k));
            trace.addSpan(span);
            log.info("Root span added: traceId={}, spanId={}", span.getTraceId(), span.getSpanId());

            linkOrphansToTrace(span.getTraceId());
        } else {
            Trace trace = traces.get(span.getTraceId());
            if (trace != null) {
                trace.addSpan(span);
                log.info("Span added to existing trace: traceId={}, spanId={}, parentId={}",
                        span.getTraceId(), span.getSpanId(), span.getParentSpanId());
            } else {
                traceOrphans = orphans.computeIfAbsent(span.getTraceId(), k -> new ArrayList<>());
                boolean alreadyExists = traceOrphans.stream()
                        .anyMatch(s -> s.getSpanId().equals(span.getSpanId()));
                if (!alreadyExists) {
                    traceOrphans.add(span);
                    orphans.put(span.getTraceId(), traceOrphans);
                    log.info("Orphan span stored: traceId={}, spanId={}, orphanCount={}",
                            span.getTraceId(), span.getSpanId(), traceOrphans.size());
                }
            }
        }
    }

    private void linkOrphansToTrace(String traceId) {
        List<Span> traceOrphans = orphans.get(traceId);
        if (traceOrphans != null && !traceOrphans.isEmpty()) {
            Trace trace = traces.get(traceId);
            if (trace != null) {
                for (Span orphan : new ArrayList<>(traceOrphans)) {
                    trace.addSpan(orphan);
                    traceOrphans.remove(orphan);
                    log.info("Orphan linked to trace: traceId={}, spanId={}", traceId, orphan.getSpanId());
                }
                if (traceOrphans.isEmpty()) {
                    orphans.remove(traceId);
                }
            }
        }
    }

    @Override
    public Optional<Trace> getTrace(String traceId) {
        return Optional.ofNullable(traces.get(traceId));
    }

    @Override
    public List<Trace> getAllTraces() {
        return new ArrayList<>(traces.values());
    }

    @Override
    public void removeSpansOlderThan(Instant cutoff) {
        int spansBefore = traces.values().stream().mapToInt(Trace::getSpanCount).sum();
        int tracesBefore = traces.size();

        traces.forEach((traceId, trace) -> {
            trace.removeSpansOlderThan(cutoff);
        });

        traces.entrySet().removeIf(entry -> entry.getValue().getSpanCount() == 0);

        orphans.forEach((traceId, orphanList) -> {
            orphanList.removeIf(span -> span.getStartTime().isBefore(cutoff));
        });
        orphans.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        int spansAfter = traces.values().stream().mapToInt(Trace::getSpanCount).sum();
        int tracesAfter = traces.size();

        log.info("Eviction complete: removed {} spans from {} traces. Now {} spans in {} traces",
                spansBefore - spansAfter, tracesBefore - tracesAfter, spansAfter, tracesAfter);
    }

    @Override
    public int getTraceCount() {
        return traces.size();
    }

    @Override
    public int getSpanCountForTrace(String traceId) {
        Trace trace = traces.get(traceId);
        return trace != null ? trace.getSpanCount() : 0;
    }

    @Override
    public List<Span> getOrphans() {
        List<Span> allOrphans = new ArrayList<>();
        orphans.values().forEach(allOrphans::addAll);
        return allOrphans;
    }

    @Override
    public int getOrphanCount() {
        return orphans.values().stream().mapToInt(List::size).sum();
    }

    @Override
    public void clear() {
        traces.clear();
        orphans.clear();
        log.info("Repository cleared");
    }
}