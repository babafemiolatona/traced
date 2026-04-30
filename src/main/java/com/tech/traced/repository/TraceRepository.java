package com.tech.traced.repository;

import com.tech.traced.models.Span;
import com.tech.traced.models.Trace;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TraceRepository {

    void addSpan(Span span);

    Optional<Trace> getTrace(String traceId);

    List<Trace> getAllTraces();

    void removeSpansOlderThan(Instant cutOff);

    int getTraceCount();

    int getSpanCountForTrace(String traceId);

    List<Span> getOrphans();

    int getOrphanCount();

    void clear();

}