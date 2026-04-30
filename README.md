# Traced

A high-performance, in-memory span ingestion server that assembles distributed traces in real-time with automatic orphan linking and configurable rolling-window eviction.

## The Problem

In microservices architectures, a single user request flows through multiple services across the network. When latency spikes or errors occur, pinpointing the culprit requires tracing the complete request path: Which service was the bottleneck? Where did the error originate?

**Real-world analogy:** Imagine ordering food at a restaurant. If your order takes 2 hours, you'd want to know: Did the kitchen run out of ingredients? Was the cashier slow? Did delivery take forever? Traced answers these questions for distributed systems.

## What It Does

**Traced** collects diagnostic information (called "spans") from multiple services, automatically assembles them into complete request traces, and maintains a memory-bounded store with configurable eviction:

- **Real-time assembly** - Connects spans into traces even when they arrive out-of-order
- **Error propagation** - Marks entire traces as failed if any service fails
- **Zero external dependencies** - Runs standalone with in-memory storage
- **Bounded memory** - Automatic rolling-window eviction every 10 seconds
- **Production tested** - Verified with 778,000+ concurrent spans

---

## Architecture Overview

```
┌─────────────────────────────────────┐
│    The Applications (Services)      │
│  checkout | payment | inventory     │
└──────────────────┬──────────────────┘
                   │
                   │ Send span data (POST /api/v1/spans)
                   ▼
┌─────────────────────────────────────┐
│        SpanController (REST API)    │
│  Validates incoming span batches    │
└──────────────────┬──────────────────┘
                   │
                   ▼
┌─────────────────────────────────────┐
│    SpanIngestService (Core Logic)   │
│  - Validates spans                  │
│  - Links orphans to traces          │
│  - Filters old data                 │
└──────────────────┬──────────────────┘
                   │
                   ▼
┌─────────────────────────────────────┐
│  InMemoryTraceRepository (Storage)  │
│  - Stores traces (ConcurrentHashMap)|
│  - Stores orphans (ConcurrentHashMap)|
│  - Thread-safe, no locks needed      │
└──────────────────┬──────────────────┘
                   │
     ┌─────────────┴─────────────┐
     ▼                           ▼
┌──────────────┐         ┌──────────────────┐
│  Traces Map  │         │  Orphans Buffer  │
│  (t1, t2...) │         │  (waiting spans) │
└──────────────┘         └──────────────────┘
     │
     ▼ (Background eviction every 10 seconds)
┌─────────────────────────────────────┐
│     EvictionService (@Scheduled)    │
│  Removes spans older than window    │
└─────────────────────────────────────┘
```

---

## Installation & Setup

### Prerequisites
- Java 17 or higher
- Docker & Docker Compose (for containerized testing)
- Maven (for building)

### Quick Start

**1. Clone and build:**
```bash
git clone https://github.com/babafemiolatona/traced.git
cd traced
mvn clean install
```

**2. Start the server:**
```bash
mvn spring-boot:run
```

The server runs on `http://localhost:8080`

**3. Test with Docker (optional):**
```bash
# Start the app in Docker
docker-compose up

# The verifier will automatically test it
```

---

## How It Works

### The Three Core Jobs

#### 1. **Accept Span Data** (POST /api/v1/spans)

The application sends diagnostic information in this format:
```json
{
  "spans": [
    {
      "spanId": "order-123",
      "traceId": "user-456",
      "parentSpanId": null,
      "serviceName": "checkout",
      "status": "ok",
      "startTime": "2026-04-27T12:00:00Z",
      "duration": 250
    }
  ]
}
```

#### 2. **Assemble Traces** (Get /api/v1/traces)

Traced automatically connects all the spans from a single request into one complete trace.

**Example trace:**
```
User Request (trace ID: order-456)
├── checkout-service (50ms) ✓
│   ├── payment-api (30ms) ✓
│   └── inventory-service (15ms) ✗ ERROR!
└── notification-service (100ms) ✓
```

Key features:
- **Automatic assembly** - Even if spans arrive out of order, Traced connects them
- **Error tracking** - If ANY step fails, the entire trace is marked as "error"
- **Dependency visibility** - You can see which services depend on which

#### 3. **Clean Up Old Data** (Background eviction)

Traced runs in memory and automatically deletes data older than 30 minutes. This keeps memory usage controlled without requiring a database.

---

## API Endpoints

### Send Span Data
```bash
POST /api/v1/spans
Content-Type: application/json

{
  "spans": [{ ... span data ... }]
}
```
**Response:** 202 Accepted (data will be processed asynchronously)

### Get All Traces
```bash
GET /api/v1/traces
```
**Response:** List of all complete traces with all their spans

### Get Specific Trace
```bash
GET /api/v1/traces/{traceId}
```
**Response:** One complete trace with detailed span information

### Get Statistics
```bash
GET /api/v1/stats
```
**Response:** 
```json
{
  "totalTraces": 1500,
  "orphanSpans": 3
}
```

---

## Key Concepts Explained

### Span
A **span** is one step in the journey. It represents one service doing one job.

*Example spans:*
- `checkout-service` receives an order
- `payment-api` processes payment
- `email-service` sends confirmation

### Trace
A **trace** is the complete journey of one request through all services.

*Example trace:* A customer's order goes through checkout → payment → inventory → notification. All 4 steps belong to one trace.

### Orphan Spans
Sometimes a span arrives before its parent.

*Why?* Network is unpredictable. If `payment-api` (child) responds faster than `checkout-service` (parent), we temporarily store the child in an "orphan buffer" until the parent arrives.

Once the parent arrives, we automatically link the orphan to the parent.

### Rolling Window
Traced stores data in a **30-minute sliding window** (configurable).

*How it works:*
- Spans older than 30 minutes are automatically deleted
- This keeps memory usage bounded (no database needed)
- Background task runs every 10 seconds to clean up

---

## Testing

### Manual Testing (Understanding What Works)

**Test 1: Single Span**
```bash
curl -X POST http://localhost:8080/api/v1/spans \
  -H "Content-Type: application/json" \
  -d '{
    "spans": [{
      "spanId": "s1",
      "traceId": "t1",
      "parentSpanId": null,
      "serviceName": "api",
      "status": "ok",
      "startTime": "2026-04-27T12:00:00Z",
      "duration": 100
    }]
  }'

# Then query it:
curl http://localhost:8080/api/v1/traces/t1
```

**Test 2: Out-of-Order Spans**
```bash
# Send child FIRST (before parent exists)
curl -X POST http://localhost:8080/api/v1/spans \
  -H "Content-Type: application/json" \
  -d '{
    "spans": [{
      "spanId": "child",
      "traceId": "t2",
      "parentSpanId": "parent",
      "serviceName": "db",
      "status": "ok",
      "startTime": "2026-04-27T12:00:00Z",
      "duration": 50
    }]
  }'

# Check stats - orphan should be waiting
curl http://localhost:8080/api/v1/stats

# Now send parent
curl -X POST http://localhost:8080/api/v1/spans \
  -H "Content-Type: application/json" \
  -d '{
    "spans": [{
      "spanId": "parent",
      "traceId": "t2",
      "parentSpanId": null,
      "serviceName": "api",
      "status": "ok",
      "startTime": "2026-04-27T12:00:00Z",
      "duration": 200
    }]
  }'

# Query trace - should have both now, linked correctly
curl http://localhost:8080/api/v1/traces/t2
```

### Automated Testing (Scale Testing)

```bash
# Run all 60 unit tests
mvn test

# Run with Docker (includes stress testing)
docker-compose up

# The verifier will:
# - Send 44,000+ spans concurrently
# - Verify every span is correctly assembled
# - Check for data loss or corruption
# - Report 100% success rate if all good
```
