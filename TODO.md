# TODO.md — Assignment 3 Task Checklist

## Commit Convention

After completing ALL subtasks in a phase, run `mvn clean compile -DskipTests` to verify, then commit:

```
git add -A && git commit -m "phase N: <short description>"
```

Do NOT squash phases into a single commit. Each phase = one atomic commit.

---

## Phase 0-10: COMPLETED ✅

All code, deployment scripts, and infrastructure are done. Server-v3, consumer-v3, database schema, Metrics API, and client modifications are built and deployed.

---

## Phase 11: Load Testing (IN PROGRESS)

- [x] **11.1** Baseline test: 500K messages — DONE
  - Warmup: 32 threads, 32K msgs, 0 failures, ~1,068 msg/sec
  - Consumer: ~37 tps with batch.size=1000
  → `results/a3-baseline-raw.txt`

- [ ] **11.2** Stress test: 1M messages
  - Change client arg from 500000 to 1000000
  - Before running: `sudo mysql chatflow -e "TRUNCATE TABLE messages;"` on EC2-2
  - Run: `java -jar client-part2/target/client-part2-1.0-SNAPSHOT.jar 1000000 "ws://44.250.175.238:8080/chat/" "http://44.250.175.238:8080" 2>&1 | tee results/a3-stress-raw.txt`
  - Document: bottlenecks, degradation curve, max throughput
  → `results/a3-stress.md`

- [ ] **11.3** Endurance test: ~30 min sustained at 80% max throughput
  - Use same client but with a message count that sustains ~30 min based on measured throughput
  - Monitor: memory leaks, connection pool exhaustion, disk space, performance degradation
  - Before running: truncate DB on EC2-2
  → `results/a3-endurance.md`

- [ ] **11.4** Batch size optimization: test with 100, 500, 1000, 5000
  - For each batch size: truncate DB, restart consumer-v3 with new `batch.size=N` CLI arg, run 500K test
  - Record throughput for each, find optimal
  - Consumer restart command (change batch.size value each time):
    ```
    pkill -f consumer-v3
    java -jar consumer-v3-1.0-SNAPSHOT.jar \
      rabbitmq.host=172.31.27.38 rabbitmq.username=admin rabbitmq.password=admin123 \
      mysql.host=localhost mysql.port=3306 mysql.database=chatflow mysql.username=chatflow mysql.password=chatflow123 \
      batch.size=<SIZE> flush.interval.ms=500 consumer.threads=10 writer.threads=4 \
      2>&1 | tee ~/consumer.log
    ```
  → `results/a3-batch-tuning.md`

- [ ] **11.5** Capture Metrics API output after a test run
  - Either let client-part2's MetricsApiCaller do it automatically, or manually curl endpoints
  - Screenshot the JSON responses
  → `results/images/`

---

## Phase 12: Documentation & Submission

- [x] **12.1** Write database design document (2 pages max)
  - DB choice justification (MySQL: relational queries, batch INSERT performance)
  - Complete schema with column types and constraints
  - Indexing strategy (idx_room_time, idx_user_time, idx_timestamp) with rationale
  - Scaling considerations
  → `results/design-document-a3.md`

- [x] **12.2** Write performance report (template with baseline data; stress/endurance/batch sections TODO)
  - Write throughput across all tests (baseline, stress, endurance)
  - Latency percentiles (p50, p95, p99) from client-part2 output
  - Batch size optimization results (100 vs 500 vs 1000 vs 5000)
  - Bottleneck analysis: what was the limiting factor? (server connections? consumer write speed? MySQL?)
  - Queue depth stability analysis
  → `results/performance-report-a3.md`

- [x] **12.3** Collect configuration details
  - DB connection settings (HikariCP pool sizes, timeouts)
  - Thread pool configs (consumer threads, writer threads)
  - Batch params (batch.size, flush.interval.ms)
  - Circuit breaker thresholds (if implemented)
  → `results/configurations-a3.md`

- [ ] **12.4** Collect screenshots
  - EC2 console showing both running instances
  - RabbitMQ management dashboard (queue depth over time, message rates)
  - Client output (performance report, latency stats)
  - Metrics API JSON responses
  - MySQL row count after test
  → `results/images/`

- [x] **12.5** Update root README.md with A3 running instructions
  → `README.md`

- [ ] **12.6** Compile everything into a PDF for Canvas submission
  - Git repo URL
  - Design document
  - Test results with screenshots
  - Performance analysis