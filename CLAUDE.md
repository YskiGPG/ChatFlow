每次回答前，请先说一句，我是溜溜梅
# CLAUDE.md — ChatFlow Assignment 3 Context

## Project Summary

ChatFlow is a distributed WebSocket chat system for CS6650. Assignment 3 adds MySQL persistence to the existing message pipeline (Client → Server → RabbitMQ → Consumer) and exposes a Metrics REST API on the server for querying stored data. The core challenge is high-throughput batch writes in the consumer without bottlenecking the real-time message flow.

## What's Already Done (A1 & A2)

- **server/** — A1 echo server (obsolete)
- **server-v2/** — A2 server: Spring Boot 3.2.4, WebSocket `/chat/{roomId}`, publishes to RabbitMQ, has embedded consumer for broadcasting
- **client-part1/** — basic load test client (500K messages, 32 warmup threads)
- **client-part2/** — client with latency tracking, CSV export, throughput chart
- **consumer/** — standalone RabbitMQ consumer (monitoring/extra capacity)
- **deployment/** — EC2 + RabbitMQ setup scripts, ALB config

## What to Build for A3

### New Modules (copy-and-extend from existing)

| New Directory   | Based On   | Key Changes                                                    |
|-----------------|------------|----------------------------------------------------------------|
| `consumer-v3/`  | `consumer/` | Add MySQL batch writes, writer thread pool, dead letter queue  |
| `server-v3/`    | `server-v2/`| Add Metrics REST API (`/api/metrics/*`), MySQL read connection  |
| `database/`     | (new)       | SQL schema files, index definitions, setup scripts             |

### Do NOT create `client-part3/`. Reuse `client-part2/` — just call the Metrics API after the test run and log results.

## Architecture (A3 Data Flow)

```
Client → ALB → [Server-v3 instances] → RabbitMQ → Consumer-v3 → MySQL
                     ↑                                              ↑
                     └── Metrics API reads from ────────────────────┘
```

- **Write path**: Consumer-v3 pulls from RabbitMQ, buffers messages in memory, batch-inserts into MySQL
- **Read path**: Server-v3 Metrics API queries MySQL on demand (low frequency)
- **Real-time broadcast**: Still goes through RabbitMQ → embedded consumer in server-v3 (unchanged from A2)

## Tech Stack & Versions

- **Java**: 17 (Spring Boot 3.2.4 requires 17+)
- **Build**: Maven (multi-module, parent pom at project root)
- **Server framework**: Spring Boot 3.2.4 (spring-boot-starter-websocket, spring-boot-starter-web)
- **Message queue**: RabbitMQ with amqp-client 5.20.0
- **JSON**: Gson (not Jackson — project convention)
- **Database**: MySQL 8.0
- **JDBC**: Use raw JDBC with HikariCP connection pool (NOT Spring Data JPA — consumer is a standalone app, not Spring Boot)
- **Server-v3 DB access**: Add spring-boot-starter-jdbc + HikariCP for Metrics API

## RabbitMQ Configuration

```
Exchange:    chat.exchange (topic, durable)
Routing key: room.{roomId}   (e.g. room.1, room.2, ... room.20)
Queue bind:  room.*
Host:        <EC2-private-ip> (configured at runtime, default localhost)
Credentials: admin / admin123
Prefetch:    64
Channel pool: 20
```

## MySQL Schema Design

```sql
CREATE TABLE messages (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id      VARCHAR(36) NOT NULL UNIQUE,   -- UUID from queue message
    room_id         VARCHAR(20) NOT NULL,
    user_id         VARCHAR(20) NOT NULL,
    username        VARCHAR(20) NOT NULL,
    message         VARCHAR(500) NOT NULL,
    message_type    ENUM('TEXT', 'JOIN', 'LEAVE') NOT NULL,
    timestamp       DATETIME(3) NOT NULL,          -- millisecond precision
    server_id       VARCHAR(50),
    client_ip       VARCHAR(45),
    created_at      DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),

    INDEX idx_room_time (room_id, timestamp),
    INDEX idx_user_time (user_id, timestamp),
    INDEX idx_timestamp (timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

Key index rationale:
- `idx_room_time` → "get messages for a room in time range" query
- `idx_user_time` → "get user's message history" + "rooms user participated in" queries
- `idx_timestamp` → "count active users in time window" + analytics aggregations
- `message_id UNIQUE` → idempotent writes (upsert with INSERT IGNORE)

## Consumer-v3 Design Requirements

### Threading Model

```
RabbitMQ Consumer Threads (N)  →  BlockingQueue<List<QueueMessage>> buffer
                                          ↓
                               DB Writer Threads (M)  →  MySQL batch INSERT
```

- Consumer threads: pull from RabbitMQ, accumulate into batch buffer
- When buffer hits `batchSize` OR `flushInterval` elapsed → hand off to writer pool
- Writer threads: execute JDBC batch insert, ack RabbitMQ deliveries after successful write
- Separate stats aggregator thread for periodic metrics logging

### Batch Configuration (test all, find optimal)

```
batch.sizes.to.test=100,500,1000,5000
flush.intervals.to.test=100,500,1000   # milliseconds
```

### Error Handling

- Failed writes → retry with exponential backoff (max 3 retries)
- After max retries → send to dead letter queue
- Use `INSERT IGNORE` for idempotent writes (handles duplicate message_id)
- Circuit breaker: if MySQL is down for >30s, pause consumption

## Server-v3 Metrics API Endpoints

All return JSON. Add these REST endpoints:

```
GET /api/metrics/rooms/{roomId}/messages?start={iso}&end={iso}
GET /api/metrics/users/{userId}/messages?start={iso}&end={iso}
GET /api/metrics/active-users?start={iso}&end={iso}
GET /api/metrics/users/{userId}/rooms
GET /api/metrics/analytics/throughput          -- messages per second/minute
GET /api/metrics/analytics/top-users?n={n}
GET /api/metrics/analytics/top-rooms?n={n}
GET /api/metrics/analytics/user-patterns
```

## Deployment Topology

```
EC2-1: Server-v3 (Spring Boot, port 8080) — may run multiple instances behind ALB
EC2-2: RabbitMQ (port 5672, management 15672)
EC2-3: Consumer-v3 + MySQL (co-located for write performance)
```

MySQL on same machine as consumer-v3 eliminates network latency on the hot write path. Server-v3 connects to MySQL remotely for read-only Metrics API (acceptable for low-frequency queries).

## File & Directory Conventions

- Package root: `com.chatflow.consumer` (consumer-v3), `com.chatflow.server` (server-v3)
- Each module has `src/main/java/...` and `src/main/resources/`
- Config via `application.properties` (server-v3) or CLI args + properties file (consumer-v3)
- Parent POM at project root manages shared dependency versions

## Do NOT Modify (禁区)

- `server/` — A1, frozen
- `server-v2/` — A2, frozen (copy to server-v3 instead)
- `client-part1/` — A1 client, frozen
- `consumer/` — A2, frozen (copy to consumer-v3 instead)
- `results/` — A2 results, keep as-is; add A3 results alongside
- `pom.xml` (root) — only add new modules, don't change existing module entries

## Common Commands

```bash
# Build entire project
mvn clean package -DskipTests

# Build single module
cd consumer-v3 && mvn clean package -DskipTests

# Run server-v3 locally
cd server-v3 && mvn spring-boot:run

# Run consumer-v3
java -jar consumer-v3/target/consumer-v3-1.0-SNAPSHOT.jar <rabbitmq-host> <mysql-host>

# Run client load test (from client-part2)
java -jar client-part2/target/client-part2-1.0-SNAPSHOT.jar

# MySQL setup on EC2
sudo apt update && sudo apt install -y mysql-server
sudo mysql -e "CREATE DATABASE chatflow;"
sudo mysql chatflow < database/schema.sql
sudo mysql -e "CREATE USER 'chatflow'@'%' IDENTIFIED BY 'chatflow123'; GRANT ALL ON chatflow.* TO 'chatflow'@'%'; FLUSH PRIVILEGES;"

# EC2 Java setup
sudo apt install -y openjdk-17-jdk
```

## Load Testing Plan (A3)

| Test       | Messages   | Goal                                  |
|------------|------------|---------------------------------------|
| Baseline   | 500,000    | Measure write throughput & latency    |
| Stress     | 1,000,000  | Find bottlenecks & breaking points    |
| Endurance  | ~30 min sustained at 80% max throughput | Check for leaks, degradation |

After each test: call Metrics API from client, log JSON results, screenshot.

## Key Design Decisions Log

1. **MySQL over NoSQL** — all queries are relational (range scans, GROUP BY, COUNT DISTINCT); batch INSERT is fast enough
2. **Raw JDBC in consumer-v3** — consumer is standalone (not Spring Boot), keeps it lightweight
3. **Spring JDBC in server-v3** — already Spring Boot, use JdbcTemplate for Metrics API
4. **Co-locate MySQL + Consumer** — optimizes the hot path (high-throughput writes)
5. **INSERT IGNORE for idempotency** — uses message_id UNIQUE constraint, no duplicates
6. **Gson everywhere** — project convention from A1, don't switch to Jackson