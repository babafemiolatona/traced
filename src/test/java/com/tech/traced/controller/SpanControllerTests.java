package com.tech.traced.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tech.traced.dto.SpanBatch;
import com.tech.traced.dto.SpanRequest;
import com.tech.traced.repository.TraceRepository;
import com.tech.traced.service.SpanIngestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("SpanController Tests")
class SpanControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SpanIngestService ingestService;

    @Autowired
    private TraceRepository traceRepository;

    @BeforeEach
    void setUp() {
        // Clear repository before each test to ensure isolation
        traceRepository.clear();
    }

    // ==================== POST /spans Tests ====================

    @Test
    @DisplayName("Should accept span batch and return 202")
    void testPostSpanBatch() throws Exception {
        // Arrange
        SpanRequest spanRequest = new SpanRequest();
        spanRequest.setSpanId("s1");
        spanRequest.setTraceId("t1");
        spanRequest.setParentSpanId(null);
        spanRequest.setServiceName("checkout");
        spanRequest.setStatus("ok");
        spanRequest.setStartTime(Instant.now());
        spanRequest.setDuration(100L);

        SpanBatch batch = new SpanBatch();
        batch.setSpans(List.of(spanRequest));

        String payload = objectMapper.writeValueAsString(batch);

        // Act & Assert
        mockMvc.perform(post("/api/v1/spans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("Should ingest multiple spans in batch")
    void testPostMultipleSpans() throws Exception {
        // Arrange
        SpanRequest span1 = new SpanRequest();
        span1.setSpanId("s1");
        span1.setTraceId("t1");
        span1.setParentSpanId(null);
        span1.setServiceName("api");
        span1.setStatus("ok");
        span1.setStartTime(Instant.now());
        span1.setDuration(200L);

        SpanRequest span2 = new SpanRequest();
        span2.setSpanId("s2");
        span2.setTraceId("t1");
        span2.setParentSpanId("s1");
        span2.setServiceName("db");
        span2.setStatus("ok");
        span2.setStartTime(Instant.now());
        span2.setDuration(100L);

        SpanBatch batch = new SpanBatch();
        batch.setSpans(List.of(span1, span2));

        String payload = objectMapper.writeValueAsString(batch);

        // Act & Assert
        mockMvc.perform(post("/api/v1/spans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("Should reject batch with null spans list")
    void testPostNullSpansList() throws Exception {
        // Arrange
        String payload = objectMapper.writeValueAsString(new SpanBatch());

        // Act & Assert
        mockMvc.perform(post("/api/v1/spans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Should reject span with missing required fields")
    void testPostSpanMissingSpanId() throws Exception {
        // Arrange
        SpanRequest spanRequest = new SpanRequest();
        // Missing spanId
        spanRequest.setTraceId("t1");
        spanRequest.setParentSpanId(null);
        spanRequest.setServiceName("svc");
        spanRequest.setStatus("ok");
        spanRequest.setStartTime(Instant.now());
        spanRequest.setDuration(100L);

        SpanBatch batch = new SpanBatch();
        batch.setSpans(List.of(spanRequest));

        String payload = objectMapper.writeValueAsString(batch);

        // Act & Assert
        mockMvc.perform(post("/api/v1/spans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Should reject span with null duration")
    void testPostSpanWithNullDuration() throws Exception {
        // Arrange
        SpanRequest spanRequest = new SpanRequest();
        spanRequest.setSpanId("s1");
        spanRequest.setTraceId("t1");
        spanRequest.setParentSpanId(null);
        spanRequest.setServiceName("svc");
        spanRequest.setStatus("ok");
        spanRequest.setStartTime(Instant.now());
        spanRequest.setDuration(null); // Null duration should be rejected

        SpanBatch batch = new SpanBatch();
        batch.setSpans(List.of(spanRequest));

        String payload = objectMapper.writeValueAsString(batch);

        // Act & Assert
        mockMvc.perform(post("/api/v1/spans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().is4xxClientError());
    }

    // ==================== GET /traces Tests ====================

    @Test
    @DisplayName("Should return all traces")
    void testGetAllTraces() throws Exception {
        // Arrange - Add some spans first
        SpanRequest spanRequest = new SpanRequest();
        spanRequest.setSpanId("s1");
        spanRequest.setTraceId("t1");
        spanRequest.setParentSpanId(null);
        spanRequest.setServiceName("svc");
        spanRequest.setStatus("ok");
        spanRequest.setStartTime(Instant.now());
        spanRequest.setDuration(100L);

        SpanBatch batch = new SpanBatch();
        batch.setSpans(List.of(spanRequest));

        mockMvc.perform(post("/api/v1/spans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(batch)))
                .andExpect(status().isAccepted());

        // Act & Assert
        mockMvc.perform(get("/api/v1/traces")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].traceId", equalTo("t1")))
                .andExpect(jsonPath("$[0].status", equalTo("ok")))
                .andExpect(jsonPath("$[0].spanCount", equalTo(1)));
    }

    @Test
    @DisplayName("Should return empty list when no traces exist")
    void testGetAllTracesEmpty() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v1/traces")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Should return correct trace count in response")
    void testGetTracesReturnsCorrectCount() throws Exception {
        // Arrange - Add multiple spans to same trace
        SpanRequest span1 = new SpanRequest();
        span1.setSpanId("s1");
        span1.setTraceId("t1");
        span1.setParentSpanId(null);
        span1.setServiceName("svc");
        span1.setStatus("ok");
        span1.setStartTime(Instant.now());
        span1.setDuration(100L);

        SpanRequest span2 = new SpanRequest();
        span2.setSpanId("s2");
        span2.setTraceId("t1");
        span2.setParentSpanId("s1");
        span2.setServiceName("svc");
        span2.setStatus("ok");
        span2.setStartTime(Instant.now());
        span2.setDuration(50L);

        SpanBatch batch = new SpanBatch();
        batch.setSpans(List.of(span1, span2));

        mockMvc.perform(post("/api/v1/spans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(batch)))
                .andExpect(status().isAccepted());

        // Act & Assert
        mockMvc.perform(get("/api/v1/traces")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].spanCount", equalTo(2)));
    }

    // ==================== GET /traces/{traceId} Tests ====================

    @Test
    @DisplayName("Should return specific trace by ID")
    void testGetTraceById() throws Exception {
        // Arrange
        SpanRequest spanRequest = new SpanRequest();
        spanRequest.setSpanId("s1");
        spanRequest.setTraceId("t1");
        spanRequest.setParentSpanId(null);
        spanRequest.setServiceName("svc");
        spanRequest.setStatus("ok");
        spanRequest.setStartTime(Instant.now());
        spanRequest.setDuration(100L);

        SpanBatch batch = new SpanBatch();
        batch.setSpans(List.of(spanRequest));

        mockMvc.perform(post("/api/v1/spans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(batch)))
                .andExpect(status().isAccepted());

        // Act & Assert
        mockMvc.perform(get("/api/v1/traces/t1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId", equalTo("t1")))
                .andExpect(jsonPath("$.status", equalTo("ok")))
                .andExpect(jsonPath("$.spanCount", equalTo(1)));
    }

    @Test
    @DisplayName("Should return 404 for non-existent trace")
    void testGetNonExistentTrace() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v1/traces/non-existent")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return detailed trace with all spans")
    void testGetTraceReturnsAllSpans() throws Exception {
        // Arrange - Add spans to trace
        SpanRequest span1 = new SpanRequest();
        span1.setSpanId("s1");
        span1.setTraceId("t1");
        span1.setParentSpanId(null);
        span1.setServiceName("api");
        span1.setStatus("ok");
        span1.setStartTime(Instant.now());
        span1.setDuration(200L);

        SpanBatch batch = new SpanBatch();
        batch.setSpans(List.of(span1));

        mockMvc.perform(post("/api/v1/spans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(batch)))
                .andExpect(status().isAccepted());

        // Delay to ensure ingestion completes
        Thread.sleep(100);

        // Act & Assert - Verify trace is returned with correct span
        mockMvc.perform(get("/api/v1/traces/t1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId", equalTo("t1")))
                .andExpect(jsonPath("$.spanCount", equalTo(1)));
    }

    @Test
    @DisplayName("Should return error status in trace response")
    void testGetTraceWithErrorStatus() throws Exception {
        // Arrange
        SpanRequest span1 = new SpanRequest();
        span1.setSpanId("s1");
        span1.setTraceId("t1");
        span1.setParentSpanId(null);
        span1.setServiceName("api");
        span1.setStatus("ok");
        span1.setStartTime(Instant.now());
        span1.setDuration(200L);

        SpanRequest span2 = new SpanRequest();
        span2.setSpanId("s2");
        span2.setTraceId("t1");
        span2.setParentSpanId("s1");
        span2.setServiceName("db");
        span2.setStatus("error");
        span2.setStartTime(Instant.now());
        span2.setDuration(100L);

        SpanBatch batch = new SpanBatch();
        batch.setSpans(List.of(span1, span2));

        mockMvc.perform(post("/api/v1/spans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(batch)))
                .andExpect(status().isAccepted());

        // Act & Assert
        mockMvc.perform(get("/api/v1/traces/t1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("error")));
    }

    // ==================== GET /stats Tests ====================

    @Test
    @DisplayName("Should return stats with trace and orphan count")
    void testGetStats() throws Exception {
        // Arrange - Add a regular trace and an orphan
        SpanRequest rootSpan = new SpanRequest();
        rootSpan.setSpanId("s1");
        rootSpan.setTraceId("t1");
        rootSpan.setParentSpanId(null);
        rootSpan.setServiceName("svc");
        rootSpan.setStatus("ok");
        rootSpan.setStartTime(Instant.now());
        rootSpan.setDuration(100L);

        SpanRequest orphanSpan = new SpanRequest();
        orphanSpan.setSpanId("s2");
        orphanSpan.setTraceId("t2");
        orphanSpan.setParentSpanId("parent");
        orphanSpan.setServiceName("svc");
        orphanSpan.setStatus("ok");
        orphanSpan.setStartTime(Instant.now());
        orphanSpan.setDuration(50L);

        SpanBatch batch = new SpanBatch();
        batch.setSpans(List.of(rootSpan, orphanSpan));

        mockMvc.perform(post("/api/v1/spans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(batch)))
                .andExpect(status().isAccepted());

        // Act & Assert
        mockMvc.perform(get("/api/v1/stats")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTraces", equalTo(1)))
                .andExpect(jsonPath("$.orphanSpans", equalTo(1)));
    }

    @Test
    @DisplayName("Should return zero stats when empty")
    void testGetStatsEmpty() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v1/stats")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTraces", equalTo(0)))
                .andExpect(jsonPath("$.orphanSpans", equalTo(0)));
    }

    // ==================== Content-Type and Format Tests ====================

    @Test
    @DisplayName("Should require Content-Type: application/json")
    void testPostRequiresJsonContentType() throws Exception {
        // Arrange
        SpanRequest spanRequest = new SpanRequest();
        spanRequest.setSpanId("s1");
        spanRequest.setTraceId("t1");
        spanRequest.setParentSpanId(null);
        spanRequest.setServiceName("svc");
        spanRequest.setStatus("ok");
        spanRequest.setStartTime(Instant.now());
        spanRequest.setDuration(100L);

        SpanBatch batch = new SpanBatch();
        batch.setSpans(List.of(spanRequest));

        // Act & Assert
        mockMvc.perform(post("/api/v1/spans")
                .contentType(MediaType.APPLICATION_XML)
                .content(objectMapper.writeValueAsString(batch)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Should return JSON response format")
    void testResponseIsJson() throws Exception {
        // Arrange
        SpanRequest spanRequest = new SpanRequest();
        spanRequest.setSpanId("s1");
        spanRequest.setTraceId("t1");
        spanRequest.setParentSpanId(null);
        spanRequest.setServiceName("svc");
        spanRequest.setStatus("ok");
        spanRequest.setStartTime(Instant.now());
        spanRequest.setDuration(100L);

        SpanBatch batch = new SpanBatch();
        batch.setSpans(List.of(spanRequest));

        mockMvc.perform(post("/api/v1/spans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(batch)))
                .andExpect(status().isAccepted());

        // Act & Assert
        mockMvc.perform(get("/api/v1/traces")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }
}
