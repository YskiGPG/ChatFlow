# Performance Report — ChatFlow Assignment 3

## 1. Test Environment

| Component | Spec |
|-----------|------|
| Server (EC2-1) | t3.small — Server-v3 + RabbitMQ |
| Consumer (EC2-2) | t3.small — Consumer-v3 + MySQL 8.0 |
| Client | Local machine → EC2-1 over public internet |
| JVM | Java 17, -Xmx512m (server) |

## 2. Baseline Test — 500K Messages

| Metric | Warmup | Main Phase | Overall |
|--------|--------|------------|---------|
| Messages | 32,000 | 468,000 | 500,000 |
| Threads | 32 | 32 | — |
| Failed | 0 | 0 | 0 |
| Runtime | 12.65s | 115.70s | 128.36s |
| Throughput | 2,529 msg/s | 4,045 msg/s | 3,895 msg/s |
| Connections | 32 | 32 | — |
| Reconnections | 0 | 0 | — |

### Latency Distribution (Overall)

| Percentile | Latency |
|------------|---------|
| Min | 0 ms |
| Median (p50) | 5 ms |
| Mean | 6.86 ms |
| p95 | 19 ms |
| p99 | 40 ms |
| Max | 421 ms |

### Message Type Distribution

| Type | Count | Percentage |
|------|-------|------------|
| TEXT | 449,841 | 90.0% |
| JOIN | 25,081 | 5.0% |
| LEAVE | 25,078 | 5.0% |

### Consumer Write Performance

- Batch size: 1,000
- Write throughput: ~37 batch writes/sec
- All 500K messages persisted to MySQL with 0 failures

## 3. Stress Test — 1M Messages

<!-- TODO: Fill in after stress test completes -->

| Metric | Warmup | Main Phase | Overall |
|--------|--------|------------|---------|
| Messages | 32,000 | 968,000 | 1,000,000 |
| Threads | | | — |
| Failed | | | |
| Runtime | | | |
| Throughput | | | |

### Latency Distribution

| Percentile | Latency |
|------------|---------|
| Median (p50) | |
| Mean | |
| p95 | |
| p99 | |
| Max | |

### Observations

<!-- TODO: Document bottlenecks, degradation curve, max throughput -->

## 4. Endurance Test — ~30 Min Sustained

<!-- TODO: Fill in after endurance test completes -->

| Metric | Value |
|--------|-------|
| Duration | |
| Total messages | |
| Throughput (start) | |
| Throughput (end) | |
| Failed messages | |
| Reconnections | |

### Stability Observations

<!-- TODO: Memory leaks, connection pool exhaustion, disk space, performance degradation -->

## 5. Batch Size Optimization

<!-- TODO: Fill in after batch tuning tests complete -->

| Batch Size | Consumer TPS | Client Throughput | Notes |
|------------|-------------|-------------------|-------|
| 100 | | | |
| 500 | | | |
| 1,000 | ~37 tps | 3,895 msg/s | Baseline |
| 5,000 | | | |

### Optimal Configuration

<!-- TODO: Which batch size gave best performance and why -->

## 6. Bottleneck Analysis

<!-- TODO: Identify the limiting factor -->

**Potential bottlenecks evaluated:**
- Client-side: WebSocket connection limit, thread contention on BlockingQueue
- Network: Public internet latency to EC2 (~5ms RTT)
- Server: WebSocket handler throughput, RabbitMQ publish rate
- RabbitMQ: Queue depth growth, consumer prefetch saturation
- Consumer: Batch accumulation speed, writer thread pool saturation
- MySQL: Disk I/O on batch INSERT, index maintenance overhead

<!-- TODO: Which was the actual bottleneck? Evidence? -->
