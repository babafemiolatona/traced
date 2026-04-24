package com.tech.traced.models;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Trace {
 
    private String traceId;
    private List<Span> spans;

    public Trace(String traceId) {
        this.traceId = traceId;
        this.spans = new CopyOnWriteArrayList<>();
    }

    public void addSpan(Span span) {
        boolean exists = spans.stream()
                .anyMatch(s -> s.getSpanId().equals(span.getSpanId()));
        if (!exists) {
            spans.add(span);
        }
    }

    public String getStatus() {
        boolean hasError = spans.stream()
                .anyMatch(Span::isError);
        return hasError ? "error" : "ok";
    }

    public Instant getFirstSpanTime() {
        return spans.stream()
                .map(Span::getStartTime)
                .min(Instant::compareTo)
                .orElse(null);
    }

    public Instant getLastSpanTime() {
        return spans.stream()
                .map(s -> s.getStartTime().plusMillis(s.getDuration()))
                .max(Instant::compareTo)
                .orElse(null);
    }

    public long getDurationMs() {
        Instant first = getFirstSpanTime();
        Instant last = getLastSpanTime();
        if (first != null && last != null) {
            return last.toEpochMilli() - first.toEpochMilli();
        }
        return 0;
    }

    public int getSpanCount() {
        return spans.size();
    }

    public boolean hasSpansOlderThan(Instant cutoff) {
        return spans.stream()
                .anyMatch(s -> s.getStartTime().isBefore(cutoff));
    }

    public void removeSpansOlderThan(Instant cutoff) {
        spans.removeIf(s -> s.getStartTime().isBefore(cutoff));
    }
}