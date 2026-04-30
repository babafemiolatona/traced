package com.tech.traced.repository;

import com.tech.traced.models.Span;
import com.tech.traced.models.Trace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("InMemoryTraceRepository Tests")
class InMemoryTraceRepositoryTests {

    @Autowired
    private TraceRepository repository;

    @BeforeEach
    void setUp() {
        // Clear repository before each test to ensure isolation
        repository.clear();
    }

    // ==================== Basic Add Span Tests ====================

    @Test
    @DisplayName("Should add root span and create trace")
    void testAddRootSpan() {
        // Arrange
        Span rootSpan = new Span("s1", "t1", null, "svc", "ok", Instant.now(), 100);

        // Act
        repository.addSpan(rootSpan);

        // Assert
        Optional<Trace> trace = repository.getTrace("t1");
        assertTrue(trace.isPresent());
        assertEquals(1, trace.get().getSpanCount());
    }

    @Test
    @DisplayName("Should add span to existing trace")
    void testAddSpanToExistingTrace() {
        // Arrange
        Span rootSpan = new Span("s1", "t1", null, "svc", "ok", Instant.now(), 100);
        Span childSpan = new Span("s2", "t1", "s1", "svc", "ok", Instant.now(), 50);

        // Act
        repository.addSpan(rootSpan);
        repository.addSpan(childSpan);

        // Assert
        Optional<Trace> trace = repository.getTrace("t1");
        assertEquals(2, trace.get().getSpanCount());
    }

    @Test
    @DisplayName("Should not add duplicate spans")
    void testNoDuplicateSpans() {
        // Arrange
        Span span = new Span("s1", "t1", null, "svc", "ok", Instant.now(), 100);

        // Act
        repository.addSpan(span);
        repository.addSpan(span); // Try to add same span again

        // Assert
        assertEquals(1, repository.getSpanCountForTrace("t1"));
    }

    @Test
    @DisplayName("Should handle null span gracefully")
    void testAddNullSpan() {
        // Act & Assert - should not throw
        assertDoesNotThrow(() -> repository.addSpan(null));
    }

    // ==================== Orphan Management Tests ====================

    @Test
    @DisplayName("Should store span as orphan when trace doesn't exist")
    void testStoreOrphanSpan() {
        // Arrange
        Span orphanSpan = new Span("s1", "t1", "parent", "svc", "ok", Instant.now(), 50);

        // Act
        repository.addSpan(orphanSpan);

        // Assert
        assertEquals(0, repository.getTraceCount(), "Trace shouldn't exist");
        assertEquals(1, repository.getOrphanCount(), "Should have 1 orphan");
    }

    @Test
    @DisplayName("Should link orphan to trace when trace is created")
    void testLinkOrphanToTrace() {
        // Arrange
        Span orphanSpan = new Span("s1", "t1", "parent", "svc", "ok", Instant.now(), 50);
        Span rootSpan = new Span("parent", "t1", null, "svc", "ok", Instant.now(), 200);

        // Act
        repository.addSpan(orphanSpan);
        assertEquals(1, repository.getOrphanCount());
        
        repository.addSpan(rootSpan);

        // Assert
        assertEquals(1, repository.getTraceCount());
        assertEquals(0, repository.getOrphanCount(), "Orphan should be linked");
        assertEquals(2, repository.getSpanCountForTrace("t1"));
    }

    @Test
    @DisplayName("Should link multiple orphans to single trace")
    void testLinkMultipleOrphans() {
        // Arrange
        Span orphan1 = new Span("s1", "t1", "parent", "svc", "ok", Instant.now(), 50);
        Span orphan2 = new Span("s2", "t1", "parent", "svc", "ok", Instant.now(), 60);
        Span orphan3 = new Span("s3", "t1", "parent", "svc", "ok", Instant.now(), 70);
        Span rootSpan = new Span("parent", "t1", null, "svc", "ok", Instant.now(), 300);

        // Act
        repository.addSpan(orphan1);
        repository.addSpan(orphan2);
        repository.addSpan(orphan3);
        assertEquals(3, repository.getOrphanCount());
        
        repository.addSpan(rootSpan);

        // Assert
        assertEquals(0, repository.getOrphanCount());
        assertEquals(4, repository.getSpanCountForTrace("t1"));
    }

    @Test
    @DisplayName("Should get all orphans")
    void testGetOrphans() {
        // Arrange
        Span orphan1 = new Span("s1", "t1", "p1", "svc", "ok", Instant.now(), 50);
        Span orphan2 = new Span("s2", "t2", "p2", "svc", "ok", Instant.now(), 60);

        // Act
        repository.addSpan(orphan1);
        repository.addSpan(orphan2);
        List<Span> orphans = repository.getOrphans();

        // Assert
        assertEquals(2, orphans.size());
    }

    // ==================== Eviction Tests ====================

    @Test
    @DisplayName("Should remove spans older than cutoff")
    void testEvictionRemovesOldSpans() {
        // Arrange
        Instant now = Instant.now();
        Instant oldTime = now.minusSeconds(40 * 60); // 40 minutes ago
        
        Span oldSpan = new Span("s1", "t1", null, "svc", "ok", oldTime, 100);
        Span recentSpan = new Span("s2", "t2", null, "svc", "ok", now, 100);

        repository.addSpan(oldSpan);
        repository.addSpan(recentSpan);
        assertEquals(2, repository.getTraceCount());

        // Act
        Instant cutoff = now.minusSeconds(30 * 60); // 30 minutes ago
        repository.removeSpansOlderThan(cutoff);

        // Assert
        assertEquals(1, repository.getTraceCount());
        assertTrue(repository.getTrace("t2").isPresent(), "Recent trace should remain");
        assertFalse(repository.getTrace("t1").isPresent(), "Old trace should be removed");
    }

    @Test
    @DisplayName("Should remove empty traces after eviction")
    void testRemoveEmptyTracesAfterEviction() {
        // Arrange
        Instant now = Instant.now();
        Instant oldTime = now.minusSeconds(40 * 60);
        
        Span oldSpan1 = new Span("s1", "t1", null, "svc", "ok", oldTime, 100);
        Span oldSpan2 = new Span("s2", "t1", "s1", "svc", "ok", oldTime, 50);

        repository.addSpan(oldSpan1);
        repository.addSpan(oldSpan2);
        assertEquals(1, repository.getTraceCount());

        // Act
        Instant cutoff = now.minusSeconds(30 * 60);
        repository.removeSpansOlderThan(cutoff);

        // Assert
        assertEquals(0, repository.getTraceCount(), "Empty trace should be removed");
    }

    @Test
    @DisplayName("Should evict orphans outside window")
    void testEvictionRemovesOldOrphans() {
        // Arrange
        Instant now = Instant.now();
        Instant oldTime = now.minusSeconds(40 * 60);
        
        Span oldOrphan = new Span("s1", "t1", "parent", "svc", "ok", oldTime, 50);
        Span recentOrphan = new Span("s2", "t2", "parent", "svc", "ok", now, 60);

        repository.addSpan(oldOrphan);
        repository.addSpan(recentOrphan);
        assertEquals(2, repository.getOrphanCount());

        // Act
        Instant cutoff = now.minusSeconds(30 * 60);
        repository.removeSpansOlderThan(cutoff);

        // Assert
        assertEquals(1, repository.getOrphanCount());
        List<Span> remainingOrphans = repository.getOrphans();
        assertEquals("s2", remainingOrphans.get(0).getSpanId());
    }

    @Test
    @DisplayName("Should keep spans at exact cutoff boundary")
    void testEvictionKeepsSpansAtCutoff() {
        // Arrange
        Instant now = Instant.now();
        Instant cutoff = now.minusSeconds(30 * 60);
        
        Span spanAtCutoff = new Span("s1", "t1", null, "svc", "ok", cutoff, 100);

        repository.addSpan(spanAtCutoff);

        // Act
        repository.removeSpansOlderThan(cutoff);

        // Assert
        assertTrue(repository.getTrace("t1").isPresent(), "Span at cutoff should remain");
    }

    // ==================== Concurrency Tests ====================

    @Test
    @DisplayName("Should handle concurrent adds safely")
    void testConcurrentAdds() throws InterruptedException {
        // Arrange
        int threadCount = 10;
        int spansPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // Act
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < spansPerThread; j++) {
                        String spanId = "s-" + threadId + "-" + j;
                        String traceId = "t-" + (j % 10); // Share 10 traces across all threads
                        Span span = new Span(spanId, traceId, null, "svc", "ok", Instant.now(), 100);
                        repository.addSpan(span);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert
        assertTrue(completed, "All threads should complete");
        assertEquals(10, repository.getTraceCount());
        long totalSpans = repository.getAllTraces().stream()
                .mapToLong(Trace::getSpanCount)
                .sum();
        assertEquals(threadCount * spansPerThread, totalSpans);
    }

    @Test
    @DisplayName("Should handle concurrent reads during eviction")
    void testConcurrentReadsDuringEviction() throws InterruptedException {
        // Arrange
        Instant now = Instant.now();
        
        // Add 50 traces
        for (int i = 0; i < 50; i++) {
            Span span = new Span("s" + i, "t" + i, null, "svc", "ok", now.minusSeconds(i * 10), 100);
            repository.addSpan(span);
        }
        assertEquals(50, repository.getTraceCount());

        int readerCount = 5;
        int writerCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(readerCount + writerCount);
        CountDownLatch latch = new CountDownLatch(readerCount + writerCount);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        // Act - Readers
        for (int i = 0; i < readerCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 10; j++) {
                        repository.getAllTraces();
                        Thread.sleep(10);
                    }
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    latch.countDown();
                }
            });
        }

        // Act - Writers/Evictors
        for (int i = 0; i < writerCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 3; j++) {
                        Instant cutoff = now.minusSeconds(20 * 60);
                        repository.removeSpansOlderThan(cutoff);
                        Thread.sleep(50);
                    }
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert
        assertTrue(completed);
        assertTrue(exceptions.isEmpty(), "No exceptions should occur during concurrent access");
    }

    // ==================== Query Tests ====================

    @Test
    @DisplayName("Should get all traces")
    void testGetAllTraces() {
        // Arrange
        Span span1 = new Span("s1", "t1", null, "svc", "ok", Instant.now(), 100);
        Span span2 = new Span("s2", "t2", null, "svc", "ok", Instant.now(), 150);
        Span span3 = new Span("s3", "t2", "s2", "svc", "ok", Instant.now(), 50);

        // Act
        repository.addSpan(span1);
        repository.addSpan(span2);
        repository.addSpan(span3);
        List<Trace> traces = repository.getAllTraces();

        // Assert
        assertEquals(2, traces.size());
    }

    @Test
    @DisplayName("Should get trace count")
    void testGetTraceCount() {
        // Arrange
        Span span1 = new Span("s1", "t1", null, "svc", "ok", Instant.now(), 100);
        Span span2 = new Span("s2", "t2", null, "svc", "ok", Instant.now(), 150);

        // Act
        repository.addSpan(span1);
        repository.addSpan(span2);

        // Assert
        assertEquals(2, repository.getTraceCount());
    }

    @Test
    @DisplayName("Should get span count for specific trace")
    void testGetSpanCountForTrace() {
        // Arrange
        Span span1 = new Span("s1", "t1", null, "svc", "ok", Instant.now(), 100);
        Span span2 = new Span("s2", "t1", "s1", "svc", "ok", Instant.now(), 50);
        Span span3 = new Span("s3", "t1", "s1", "svc", "ok", Instant.now(), 60);

        // Act
        repository.addSpan(span1);
        repository.addSpan(span2);
        repository.addSpan(span3);

        // Assert
        assertEquals(3, repository.getSpanCountForTrace("t1"));
    }

    @Test
    @DisplayName("Should return 0 for non-existent trace span count")
    void testGetSpanCountForNonExistentTrace() {
        // Act
        int count = repository.getSpanCountForTrace("non-existent");

        // Assert
        assertEquals(0, count);
    }

    @Test
    @DisplayName("Should return empty optional for non-existent trace")
    void testGetNonExistentTrace() {
        // Act
        Optional<Trace> trace = repository.getTrace("non-existent");

        // Assert
        assertFalse(trace.isPresent());
    }

    @Test
    @DisplayName("Should get orphan count")
    void testGetOrphanCount() {
        // Arrange
        Span orphan1 = new Span("s1", "t1", "p1", "svc", "ok", Instant.now(), 50);
        Span orphan2 = new Span("s2", "t1", "p1", "svc", "ok", Instant.now(), 60);

        // Act
        repository.addSpan(orphan1);
        repository.addSpan(orphan2);

        // Assert
        assertEquals(2, repository.getOrphanCount());
    }
}
