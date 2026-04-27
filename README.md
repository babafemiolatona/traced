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
