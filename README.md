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
