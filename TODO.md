# TODO.md — Assignment 3 Task Checklist

## Commit Convention

After completing ALL subtasks in a phase, run `mvn clean compile -DskipTests` to verify, then commit:

```
git add -A && git commit -m "phase N: <short description>"
```

| Phase | Commit Message |
|-------|---------------|
| 0 | `phase 0: scaffold consumer-v3, server-v3, database modules` |
| 1 | `phase 1: add MySQL schema and setup script` |
| 2 | `phase 2: consumer-v3 MySQL deps and config` |
| 3 | `phase 3: implement JDBC batch writer` |
| 4 | `phase 4: message buffer and writer thread pool` |
| 5 | `phase 5: wire RabbitMQ consumer to DB pipeline` |
| 6 | `phase 6: server-v3 MySQL read dependencies` |
| 7 | `phase 7: metrics API core query endpoints` |
| 8 | `phase 8: metrics API analytics endpoints` |
| 9 | `phase 9: client metrics API caller` |
| 10 | `phase 10: deployment scripts for MySQL and consumer-v3` |

Do NOT squash phases into a single commit. Each phase = one atomic commit.

---

## Phase 0: Project Scaffolding

- [x] **0.1** Add `consumer-v3` and `server-v3` to root `pom.xml` `<modules>` section
  → `pom.xml`

- [x] **0.2** Copy `consumer/` to `consumer-v3/`, update `pom.xml` artifactId to `consumer-v3`
  → `consumer-v3/pom.xml`

- [x] **0.3** Copy `server-v2/` to `server-v3/`, update `pom.xml` artifactId to `server-v3`
  → `server-v3/pom.xml`

- [x] **0.4** Create `database/` directory with empty `schema.sql` and `setup.sh`
  → `database/schema.sql`, `database/setup.sh`

- [x] **0.5** Verify `mvn clean compile` passes for both new modules
  → terminal

---

## Phase 1: Database Schema & Setup

- [x] **1.1** Write `schema.sql`: CREATE DATABASE, CREATE TABLE `messages` with all columns (message_id UUID unique, room_id, user_id, username, message, message_type ENUM, timestamp DATETIME(3), server_id, client_ip, created_at)
  → `database/schema.sql`

- [x] **1.2** Add indexes: composite `(room_id, timestamp)`, composite `(user_id, timestamp)`, single `(timestamp)`
  → `database/schema.sql`

- [x] **1.3** Write `setup.sh`: install MySQL, create database, run schema, create user with remote access, open port 3306 in firewall
  → `database/setup.sh`

---

## Phase 2: Consumer-v3 — MySQL Dependencies & Config

- [x] **2.1** Add MySQL connector and HikariCP dependencies to consumer-v3 `pom.xml`
  → `consumer-v3/pom.xml`

- [x] **2.2** Create `config/ConsumerConfig.java`: load properties for MySQL (host, port, db, user, password), RabbitMQ (host, credentials), batch settings (batchSize, flushIntervalMs), thread pool sizes (consumerThreads, writerThreads)
  → `consumer-v3/src/main/java/com/chatflow/consumer/config/ConsumerConfig.java`

- [x] **2.3** Create `consumer-v3/src/main/resources/consumer.properties` with default values
  → `consumer-v3/src/main/resources/consumer.properties`

---

## Phase 3: Consumer-v3 — Database Writer

- [x] **3.1** Create `db/DatabaseManager.java`: HikariCP pool initialization, `getConnection()`, `shutdown()` methods
  → `consumer-v3/src/main/java/com/chatflow/consumer/db/DatabaseManager.java`

- [x] **3.2** Create `db/MessageBatchWriter.java`: accepts `List<QueueMessage>`, executes JDBC batch `INSERT IGNORE INTO messages (...) VALUES (?,?,?,?,?,?,?,?,?)`, returns success/failure count
  → `consumer-v3/src/main/java/com/chatflow/consumer/db/MessageBatchWriter.java`

- [x] **3.3** Unit test `MessageBatchWriter` with embedded/mock DB — verify batch insert logic, duplicate handling via INSERT IGNORE
  → `consumer-v3/src/test/java/com/chatflow/consumer/db/MessageBatchWriterTest.java`

---

## Phase 4: Consumer-v3 — Buffering & Batch Flush

- [x] **4.1** Create `model/QueueMessage.java`: POJO matching the RabbitMQ message JSON (messageId, roomId, userId, username, message, timestamp, messageType, serverId, clientIp) + deliveryTag for ack
  → `consumer-v3/src/main/java/com/chatflow/consumer/model/QueueMessage.java`

- [x] **4.2** Create `buffer/MessageBuffer.java`: thread-safe buffer using `BlockingQueue` or `ConcurrentLinkedQueue`. Methods: `add(QueueMessage)`, `drainBatch(int batchSize)`. Auto-flush when buffer size ≥ batchSize OR flushInterval elapsed (use ScheduledExecutorService timer)
  → `consumer-v3/src/main/java/com/chatflow/consumer/buffer/MessageBuffer.java`

- [x] **4.3** Create `writer/DatabaseWriterPool.java`: fixed thread pool of M writer threads. Each pulls a batch from MessageBuffer, calls MessageBatchWriter, on success acks RabbitMQ deliveries, on failure retries with exponential backoff (max 3), then sends to dead letter
  → `consumer-v3/src/main/java/com/chatflow/consumer/writer/DatabaseWriterPool.java`

- [x] **4.4** Unit test buffer: verify thread-safe add, drain batch correctness, flush-on-interval behavior
  → `consumer-v3/src/test/java/com/chatflow/consumer/buffer/MessageBufferTest.java`

---

## Phase 5: Consumer-v3 — RabbitMQ Integration

- [x] **5.1** Refactor `ConsumerApp.java`: replace inline consume logic with new pipeline: RabbitMQ callback → parse JSON to QueueMessage → add to MessageBuffer
  → `consumer-v3/src/main/java/com/chatflow/consumer/ConsumerApp.java`

- [x] **5.2** Add graceful shutdown hook: stop consuming, flush remaining buffer to DB, close HikariCP pool, close RabbitMQ connection
  → `consumer-v3/src/main/java/com/chatflow/consumer/ConsumerApp.java`

- [x] **5.3** Add metrics tracking: AtomicLong counters for messagesConsumed, messagesWritten, messagesFailed, batchesExecuted. Log every 10 seconds
  → `consumer-v3/src/main/java/com/chatflow/consumer/metrics/ConsumerMetrics.java`

- [x] **5.4** Integration test: start embedded RabbitMQ + MySQL, publish 100 messages, verify all persisted in DB
  → `consumer-v3/src/test/java/com/chatflow/consumer/integration/PipelineIntegrationTest.java`

---

## Phase 6: Server-v3 — MySQL Read Dependencies

- [x] **6.1** Add `spring-boot-starter-jdbc` dependency to server-v3 `pom.xml`
  → `server-v3/pom.xml`

- [x] **6.2** Add MySQL connection properties to `application.properties` (spring.datasource.url, username, password, hikari pool settings)
  → `server-v3/src/main/resources/application.properties`

---

## Phase 7: Server-v3 — Metrics API (Core Queries)

- [x] **7.1** Create `repository/MessageRepository.java`: JdbcTemplate-based DAO with methods: `findByRoomAndTimeRange(roomId, start, end)`, `findByUserAndTimeRange(userId, start, end)`, `countActiveUsers(start, end)`, `findRoomsByUser(userId)`
  → `server-v3/src/main/java/com/chatflow/server/repository/MessageRepository.java`

- [x] **7.2** Create `controller/MetricsController.java` with 4 core query endpoints:
  - `GET /api/metrics/rooms/{roomId}/messages?start=&end=`
  - `GET /api/metrics/users/{userId}/messages?start=&end=`
  - `GET /api/metrics/active-users?start=&end=`
  - `GET /api/metrics/users/{userId}/rooms`
  → `server-v3/src/main/java/com/chatflow/server/controller/MetricsController.java`

- [x] **7.3** Test each core endpoint with mock repository
  → `server-v3/src/test/java/com/chatflow/server/controller/MetricsControllerTest.java`

---

## Phase 8: Server-v3 — Metrics API (Analytics Queries)

- [x] **8.1** Add analytics methods to `MessageRepository.java`: `getThroughputStats()` (messages per second/minute), `getTopUsers(n)`, `getTopRooms(n)`, `getUserPatterns()`
  → `server-v3/src/main/java/com/chatflow/server/repository/MessageRepository.java`

- [x] **8.2** Add 4 analytics endpoints to `MetricsController.java`:
  - `GET /api/metrics/analytics/throughput`
  - `GET /api/metrics/analytics/top-users?n=`
  - `GET /api/metrics/analytics/top-rooms?n=`
  - `GET /api/metrics/analytics/user-patterns`
  → `server-v3/src/main/java/com/chatflow/server/controller/MetricsController.java`

- [x] **8.3** Test analytics endpoints
  → `server-v3/src/test/java/com/chatflow/server/controller/MetricsControllerTest.java`

---

## Phase 9: Client — Metrics API Caller

- [x] **9.1** Add `metrics/MetricsApiCaller.java` to `client-part2`: after load test completes, send HTTP GET to each Metrics API endpoint, log full JSON response to console
  → `client-part2/src/main/java/com/chatflow/client/metrics/MetricsApiCaller.java`

- [x] **9.2** Wire into `ClientApp.java`: call MetricsApiCaller after all messages sent, before printing final stats
  → `client-part2/src/main/java/com/chatflow/client/ClientApp.java`

---

## Phase 10: Deployment & EC2 Setup

- [x] **10.1** Write `deployment/setup-mysql.sh`: install MySQL 8.0, secure installation, create database + user, apply schema, configure bind-address for remote access
  → `deployment/setup-mysql.sh`

- [x] **10.2** Update `deployment/deploy-server.sh` for server-v3 JAR
  → `deployment/deploy-server.sh`

- [x] **10.3** Create `deployment/deploy-consumer-v3.sh`: scp JAR, start with correct RabbitMQ + MySQL host args
  → `deployment/deploy-consumer-v3.sh`

- [x] **10.4** Update EC2 security groups: open MySQL port 3306 between consumer and server instances (internal only)
  → `deployment/` docs

---

## Phase 11: Load Testing

- [ ] **11.1** Run baseline test: 500K messages, record write throughput, latency percentiles, queue depth
  → `results/a3-baseline.md`

- [ ] **11.2** Run stress test: 1M messages, identify bottlenecks, document degradation
  → `results/a3-stress.md`

- [ ] **11.3** Run endurance test: ~30 min at 80% max throughput, monitor memory/connections/disk
  → `results/a3-endurance.md`

- [ ] **11.4** Test batch size optimization: run with 100, 500, 1000, 5000 batch sizes, compare throughput
  → `results/a3-batch-tuning.md`

- [ ] **11.5** Capture Metrics API output: screenshot client logs showing JSON responses from all endpoints
  → `results/images/`

---

## Phase 12: Documentation & Submission

- [ ] **12.1** Write database design document (2 pages max): DB choice justification, schema, indexing strategy, scaling considerations
  → `results/design-document-a3.md`

- [ ] **12.2** Write performance report: write throughput, latency percentiles, batch optimization results, bottleneck analysis
  → `results/performance-report-a3.md`

- [ ] **12.3** Collect all config files: DB connection settings, thread pool configs, batch params, circuit breaker thresholds
  → `results/configurations-a3.md`

- [ ] **12.4** Take EC2 console screenshots, RabbitMQ management screenshots, MySQL metrics
  → `results/images/`

- [ ] **12.5** Update root `README.md` with A3 running instructions
  → `README.md`
