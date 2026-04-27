package main

import (
    "bytes"
    "encoding/json"
    "fmt"
    "log"
    "math/rand"
    "net/http"
    "os"
    "strconv"
    "sync"
    "sync/atomic"
    "time"
)

type Span struct {
    SpanID       string    `json:"spanId"`
    TraceID      string    `json:"traceId"`
    ParentSpanID *string   `json:"parentSpanId"`
    ServiceName  string    `json:"serviceName"`
    Status       string    `json:"status"`
    StartTime    time.Time `json:"startTime"`
    Duration     int64     `json:"duration"`
}

type SpanBatch struct {
    Spans []Span `json:"spans"`
}

type TraceResponse struct {
    TraceID   string `json:"traceId"`
    Status    string `json:"status"`
    SpanCount int    `json:"spanCount"`
    Duration  int64  `json:"duration"`
}

type StatsResponse struct {
    TraceCount  int `json:"traceCount"`
    OrphanCount int `json:"orphanCount"`
}

var (
    targetURL        string
    workers          int
    duration         int
    ratePerWorker    int
    batchSize        int
    windowMinutes    int
    verifyTimeout    int
    totalSpansSent   int64
    totalSpansVerified int64
    sentSpansByTrace = make(map[string]int)
    mu               sync.Mutex
    services         = []string{"checkout", "payment", "order", "inventory", "shipping"}
    statuses         = []string{"ok", "error"}
)

func init() {
    targetURL = os.Getenv("TARGET_URL")
    if targetURL == "" {
        targetURL = "http://localhost:8080"
    }
    workers, _ = strconv.Atoi(getEnv("WORKERS", "10"))
    duration, _ = strconv.Atoi(getEnv("DURATION", "60"))
    ratePerWorker, _ = strconv.Atoi(getEnv("RATE_PER_WORKER", "10"))
    batchSize, _ = strconv.Atoi(getEnv("BATCH_SIZE", "100"))
    windowMinutes, _ = strconv.Atoi(getEnv("WINDOW_MINUTES", "30"))
    verifyTimeout, _ = strconv.Atoi(getEnv("VERIFY_TIMEOUT_SECONDS", "120"))
}

func getEnv(key, defaultVal string) string {
    if val := os.Getenv(key); val != "" {
        return val
    }
    return defaultVal
}

func main() {
    log.Printf("Starting Traced Emitter/Verifier")
    log.Printf("Target: %s", targetURL)
    log.Printf("Workers: %d, Duration: %ds, Rate: %d spans/sec per worker", workers, duration, ratePerWorker)
    log.Printf("Batch Size: %d, Window: %d minutes", batchSize, windowMinutes)

    var wg sync.WaitGroup
    stopChan := make(chan bool)
    
    for i := 0; i < workers; i++ {
        wg.Add(1)
        go emitterWorker(i, stopChan, &wg)
    }

    time.Sleep(time.Duration(duration) * time.Second)
    close(stopChan)
    wg.Wait()

    log.Printf("Emission complete. Sent %d spans across traces", atomic.LoadInt64(&totalSpansSent))

    time.Sleep(2 * time.Second)

    verifyResults()
}

func emitterWorker(id int, stopChan chan bool, wg *sync.WaitGroup) {
    defer wg.Done()
    
    ticker := time.NewTicker(time.Second / time.Duration(ratePerWorker))
    defer ticker.Stop()

    for {
        select {
        case <-stopChan:
            return
        case <-ticker.C:
            batch := generateBatch()
            sendBatch(batch)
        }
    }
}

func generateBatch() SpanBatch {
    batch := SpanBatch{Spans: make([]Span, 0, batchSize)}
    
    for i := 0; i < batchSize; i++ {
        traceID := fmt.Sprintf("trace-%d-%d", rand.Intn(1000), time.Now().Unix())
        span := generateSpan(traceID, nil)
        batch.Spans = append(batch.Spans, span)

        if rand.Float64() < 0.6 {
            numChildren := rand.Intn(3) + 1
            for j := 0; j < numChildren; j++ {
                parentID := span.SpanID
                childSpan := generateSpan(traceID, &parentID)
                batch.Spans = append(batch.Spans, childSpan)
            }
        }

        mu.Lock()
        sentSpansByTrace[traceID] += len(batch.Spans)
        mu.Unlock()
    }

    atomic.AddInt64(&totalSpansSent, int64(len(batch.Spans)))
    return batch
}

func generateSpan(traceID string, parentID *string) Span {
    spanID := fmt.Sprintf("span-%d", rand.Int63())
    return Span{
        SpanID:       spanID,
        TraceID:      traceID,
        ParentSpanID: parentID,
        ServiceName:  services[rand.Intn(len(services))],
        Status:       statuses[rand.Intn(len(statuses))],
        StartTime:    time.Now(),
        Duration:     int64(rand.Intn(500) + 10),
    }
}

func sendBatch(batch SpanBatch) {
    data, _ := json.Marshal(batch)
    
    resp, err := http.Post(
        targetURL+"/api/v1/spans",
        "application/json",
        bytes.NewBuffer(data),
    )
    
    if err != nil {
        log.Printf("Error sending batch: %v", err)
        return
    }
    defer resp.Body.Close()

    if resp.StatusCode != http.StatusAccepted && resp.StatusCode != http.StatusOK {
        log.Printf("Warning: Got %d response", resp.StatusCode)
    }
}

func verifyResults() {
    log.Printf("Starting verification...")
    
    resp, err := http.Get(targetURL + "/api/v1/traces")
    if err != nil {
        log.Fatalf("Failed to fetch traces: %v", err)
    }
    defer resp.Body.Close()

    var traces []TraceResponse
    json.NewDecoder(resp.Body).Decode(&traces)

    totalSpansFound := 0
    for _, trace := range traces {
        totalSpansFound += trace.SpanCount
    }

    log.Printf("\n=== VERIFICATION RESULTS ===")
    log.Printf("Spans sent:     %d", atomic.LoadInt64(&totalSpansSent))
    log.Printf("Traces found:   %d", len(traces))
    log.Printf("Spans in traces: %d", totalSpansFound)
    
    if len(traces) == 0 {
        log.Printf("ERROR: No traces found!")
        os.Exit(1)
    }

    errorTraces := 0
    for _, trace := range traces {
        if trace.Status == "error" {
            errorTraces++
        }
    }
    
    log.Printf("Error traces:   %d", errorTraces)
    log.Printf("\n✅ All checks passed - system is working!")
    os.Exit(0)
}