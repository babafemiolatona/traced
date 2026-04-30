package com.tech.traced.service;

import com.tech.traced.models.Span;
import com.tech.traced.models.Trace;
import com.tech.traced.repository.TraceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("SpanIngestService Tests")
class SpanIngestServiceTests {

    @Autowired
    private SpanIngestService ingestService;

    @Autowired
    private TraceRepository traceRepository;

    @BeforeEach
    void setUp() {
        // Clear repository before each test to ensure isolation
        traceRepository.clear();
    }

    // ==================== Basic Ingestion Tests ====================

    @Test
    @DisplayName("Should ingest single root span and create trace")
    void testIngestSingleRootSpan() {
        // Arrange
        Span rootSpan = new Span(
            "s1", "t1", null, "checkout", "ok", Instant.now(), 100
        );

        // Act
        ingestService.ingestBatch(List.of(rootSpan));

        // Assert
        Optional<Trace> trace = traceRepository.getTrace("t1");
        assertTrue(trace.isPresent(), "Trace should exist");
        assertEquals(1, trace.get().getSpans().size());
        assertEquals("s1", trace.get().getSpans().get(0).getSpanId());
    }

    @Test
    @DisplayName("Should handle empty batch gracefully")
    void testIngestEmptyBatch() {
        // Act
        ingestService.ingestBatch(List.of());

        // Assert
        assertEquals(0, ingestService.getTraceCount());
    }

    @Test
    @DisplayName("Should handle null batch gracefully")
    void testIngestNullBatch() {
        // Act
        ingestService.ingestBatch(null);

        // Assert
        assertEquals(0, ingestService.getTraceCount());
    }

    // ==================== Out-of-Order Assembly Tests ====================

    @Test
    @DisplayName("Should buffer child span as orphan when parent doesn't exist yet")
    void testOrphanBufferingWhenParentMissing() {
        // Arrange
        Span childSpan = new Span(
            "s2-child", "t2", "s2-parent", "payment", "ok", Instant.now(), 50
        );

        // Act
        ingestService.ingestBatch(List.of(childSpan));

        // Assert
        assertEquals(0, ingestService.getTraceCount(), "Trace shouldn't exist yet");
        assertEquals(1, ingestService.getOrphanCount(), "Child should be in orphan buffer");
    }

    @Test
    @DisplayName("Should link orphan to parent when parent arrives")
    void testOrphanLinkingWhenParentArrives() {
        // Arrange
        Span childSpan = new Span(
            "s2-child", "t2", "s2-parent", "payment", "ok", Instant.now(), 50
        );
        Span parentSpan = new Span(
            "s2-parent", "t2", null, "payment", "ok", Instant.now(), 200
        );

        // Act
        ingestService.ingestBatch(List.of(childSpan));
        assertEquals(1, ingestService.getOrphanCount(), "Initial: 1 orphan");
        
        ingestService.ingestBatch(List.of(parentSpan));

        // Assert
        assertEquals(1, ingestService.getTraceCount(), "Trace should exist now");
        assertEquals(0, ingestService.getOrphanCount(), "Orphan should be linked");
        
        Optional<Trace> trace = traceRepository.getTrace("t2");
        assertTrue(trace.isPresent());
        assertEquals(2, trace.get().getSpans().size(), "Should have both parent and child");
    }

    @Test
    @DisplayName("Should handle multiple orphans linking to one parent")
    void testMultipleOrphansLinking() {
        // Arrange
        Span child1 = new Span("s3-child1", "t3", "s3-parent", "db", "ok", Instant.now(), 50);
        Span child2 = new Span("s3-child2", "t3", "s3-parent", "cache", "ok", Instant.now(), 30);
        Span parent = new Span("s3-parent", "t3", null, "api", "ok", Instant.now(), 500);

        // Act
        ingestService.ingestBatch(List.of(child1, child2));
        assertEquals(2, ingestService.getOrphanCount());
        
        ingestService.ingestBatch(List.of(parent));

        // Assert
        assertEquals(0, ingestService.getOrphanCount());
        Optional<Trace> trace = traceRepository.getTrace("t3");
        assertEquals(3, trace.get().getSpans().size());
    }

    // ==================== Error Status Propagation Tests ====================

    @Test
    @DisplayName("Should mark trace as error if any span is error")
    void testErrorStatusPropagation() {
        // Arrange
        Span okSpan = new Span("s4-ok", "t4", null, "api", "ok", Instant.now(), 200);
        Span errorSpan = new Span("s4-error", "t4", "s4-ok", "db", "error", Instant.now(), 150);

        // Act
        ingestService.ingestBatch(List.of(okSpan, errorSpan));

        // Assert
        Optional<Trace> trace = traceRepository.getTrace("t4");
        assertTrue(trace.isPresent());
        assertEquals("error", trace.get().getStatus(), "Trace status should be error");
    }

    @Test
    @DisplayName("Should mark trace as ok if all spans are ok")
    void testOkStatusWhenAllSpansOk() {
        // Arrange
        Span span1 = new Span("s5-1", "t5", null, "api", "ok", Instant.now(), 200);
        Span span2 = new Span("s5-2", "t5", "s5-1", "db", "ok", Instant.now(), 100);

        // Act
        ingestService.ingestBatch(List.of(span1, span2));

        // Assert
        Optional<Trace> trace = traceRepository.getTrace("t5");
        assertEquals("ok", trace.get().getStatus());
    }

    // ==================== Batch Processing Tests ====================

    @Test
    @DisplayName("Should process multiple spans in single batch")
    void testMultipleSpansBatch() {
        // Arrange
        Span root = new Span("s6-root", "t6", null, "order", "ok", Instant.now(), 500);
        Span db = new Span("s6-db", "t6", "s6-root", "db", "ok", Instant.now(), 100);
        Span cache = new Span("s6-cache", "t6", "s6-root", "cache", "ok", Instant.now(), 50);

        // Act
        ingestService.ingestBatch(List.of(root, db, cache));

        // Assert
        assertEquals(1, ingestService.getTraceCount());
        assertEquals(3, ingestService.getSpanCountForTrace("t6"));
    }

    @Test
    @DisplayName("Should process spans for multiple traces in one batch")
    void testMultipleTracesInBatch() {
        // Arrange
        Span t1span = new Span("s1", "t1", null, "svc1", "ok", Instant.now(), 100);
        Span t2span = new Span("s2", "t2", null, "svc2", "ok", Instant.now(), 150);

        // Act
        ingestService.ingestBatch(List.of(t1span, t2span));

        // Assert
        assertEquals(2, ingestService.getTraceCount());
    }

    // ==================== Validation Tests ====================

    @Test
    @DisplayName("Should reject spans with missing spanId")
    void testRejectMissingSpanId() {
        // Arrange
        Span invalidSpan = new Span(null, "t7", null, "svc", "ok", Instant.now(), 100);

        // Act
        ingestService.ingestBatch(List.of(invalidSpan));

        // Assert
        assertEquals(0, ingestService.getTraceCount(), "Span should be rejected");
    }

    @Test
    @DisplayName("Should reject spans with missing traceId")
    void testRejectMissingTraceId() {
        // Arrange
        Span invalidSpan = new Span("s1", null, null, "svc", "ok", Instant.now(), 100);

        // Act
        ingestService.ingestBatch(List.of(invalidSpan));

        // Assert
        assertEquals(0, ingestService.getTraceCount());
    }

    @Test
    @DisplayName("Should reject spans with missing serviceName")
    void testRejectMissingServiceName() {
        // Arrange
        Span invalidSpan = new Span("s1", "t8", null, null, "ok", Instant.now(), 100);

        // Act
        ingestService.ingestBatch(List.of(invalidSpan));

        // Assert
        assertEquals(0, ingestService.getTraceCount());
    }

    @Test
    @DisplayName("Should reject spans with missing startTime")
    void testRejectMissingStartTime() {
        // Arrange
        Span invalidSpan = new Span("s1", "t9", null, "svc", "ok", null, 100);

        // Act
        ingestService.ingestBatch(List.of(invalidSpan));

        // Assert
        assertEquals(0, ingestService.getTraceCount());
    }

    @Test
    @DisplayName("Should reject spans with negative duration")
    void testRejectNegativeDuration() {
        // Arrange
        Span invalidSpan = new Span("s1", "t10", null, "svc", "ok", Instant.now(), -50);

        // Act
        ingestService.ingestBatch(List.of(invalidSpan));

        // Assert
        assertEquals(0, ingestService.getTraceCount());
    }

    @Test
    @DisplayName("Should validate span correctly")
    void testValidateSpan() {
        // Arrange
        Span validSpan = new Span("s1", "t1", null, "svc", "ok", Instant.now(), 100);
        
        // Act
        boolean result = ingestService.validateSpan(validSpan);
        
        // Assert
        assertTrue(result);
    }

    @Test
    @DisplayName("Should reject null span in validation")
    void testValidateNullSpan() {
        // Act
        boolean result = ingestService.validateSpan(null);
        
        // Assert
        assertFalse(result);
    }

    // ==================== Rolling Window Tests ====================

    @Test
    @DisplayName("Should reject spans outside rolling window")
    void testRejectSpansOutsideWindow() {
        // Arrange - Span from 40 minutes ago (outside 30 minute window)
        Instant tooOld = Instant.now().minusSeconds(40 * 60);
        Span oldSpan = new Span("s1", "t1", null, "svc", "ok", tooOld, 100);

        // Act
        ingestService.ingestBatch(List.of(oldSpan));

        // Assert
        assertEquals(0, ingestService.getTraceCount(), "Old span should be rejected");
    }

    @Test
    @DisplayName("Should accept spans within rolling window")
    void testAcceptSpansWithinWindow() {
        // Arrange - Span from 10 minutes ago (within 30 minute window)
        Instant recent = Instant.now().minusSeconds(10 * 60);
        Span recentSpan = new Span("s1", "t1", null, "svc", "ok", recent, 100);

        // Act
        ingestService.ingestBatch(List.of(recentSpan));

        // Assert
        assertEquals(1, ingestService.getTraceCount());
    }

    // ==================== Query Tests ====================

    @Test
    @DisplayName("Should retrieve all traces")
    void testGetAllTraces() {
        // Arrange
        Span span1 = new Span("s1", "t1", null, "svc1", "ok", Instant.now(), 100);
        Span span2 = new Span("s2", "t2", null, "svc2", "ok", Instant.now(), 150);

        // Act
        ingestService.ingestBatch(List.of(span1, span2));
        List<Trace> traces = ingestService.getAllTraces();

        // Assert
        assertEquals(2, traces.size());
    }

    @Test
    @DisplayName("Should get specific trace by ID")
    void testGetTraceById() {
        // Arrange
        Span span = new Span("s1", "t1", null, "svc", "ok", Instant.now(), 100);

        // Act
        ingestService.ingestBatch(List.of(span));
        Optional<Trace> trace = ingestService.getTrace("t1");

        // Assert
        assertTrue(trace.isPresent());
        assertEquals("t1", trace.get().getTraceId());
    }

    @Test
    @DisplayName("Should return empty optional for non-existent trace")
    void testGetNonExistentTrace() {
        // Act
        Optional<Trace> trace = ingestService.getTrace("non-existent");

        // Assert
        assertFalse(trace.isPresent());
    }

    @Test
    @DisplayName("Should return correct orphan count")
    void testGetOrphanCount() {
        // Arrange
        Span orphan1 = new Span("s1", "t1", "parent1", "svc", "ok", Instant.now(), 50);
        Span orphan2 = new Span("s2", "t2", "parent2", "svc", "ok", Instant.now(), 60);

        // Act
        ingestService.ingestBatch(List.of(orphan1, orphan2));

        // Assert
        assertEquals(2, ingestService.getOrphanCount());
    }
}
