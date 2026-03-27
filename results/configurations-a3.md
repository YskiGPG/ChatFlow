# Configuration Details — ChatFlow Assignment 3

## Deployment Topology

| Instance | Role | Type | Public IP | Private IP |
|----------|------|------|-----------|------------|
| EC2-1 (chatflow-server) | Server-v3 + RabbitMQ | t3.small | 44.250.175.238 | 172.31.27.38 |
| EC2-2 (chatflow-consumer) | Consumer-v3 + MySQL | t3.small | 34.219.58.22 | 172.31.23.176 |

## RabbitMQ Configuration

| Parameter | Value |
|-----------|-------|
| Exchange | `chat.exchange` (topic, durable) |
| Queue | `consumer-v3-db-queue` (bound to `room.*`) |
| Host | 172.31.27.38 (EC2-1 private IP) |
| Credentials | admin / admin123 |
| Consumer prefetch | 64 |
| Channel pool size | 20 |
| Auto-recovery | Enabled (5-second interval) |

## MySQL Configuration

| Parameter | Value |
|-----------|-------|
| Host | localhost (EC2-2) |
| Port | 3306 |
| Database | chatflow |
| Credentials | chatflow / chatflow123 |
| Engine | InnoDB |
| Character set | utf8mb4 |
| bind-address | 0.0.0.0 (remote access from server-v3) |

## HikariCP Connection Pool — Consumer-v3 (Write Path)

| Parameter | Value |
|-----------|-------|
| Pool name | ChatFlowConsumerPool |
| Maximum pool size | 10 |
| Minimum idle | 2 |
| Connection timeout | 30,000 ms (30s) |
| Idle timeout | 600,000 ms (10 min) |
| Max lifetime | 1,800,000 ms (30 min) |
| Prepared statement cache | 250 entries, 2048 SQL limit |

## HikariCP Connection Pool — Server-v3 (Read Path / Metrics API)

| Parameter | Value |
|-----------|-------|
| Pool name | ServerMetricsPool |
| Maximum pool size | 5 |
| Minimum idle | 1 |
| Connection timeout | 30,000 ms (30s) |
| Idle timeout | 600,000 ms (10 min) |
| Max lifetime | 1,800,000 ms (30 min) |

## Consumer-v3 Thread & Batch Configuration

| Parameter | Value | Description |
|-----------|-------|-------------|
| consumer.threads | 10 | RabbitMQ consumer threads (each with own channel) |
| writer.threads | 4 | Database writer thread pool size |
| batch.size | 1000 | Messages per batch INSERT |
| flush.interval.ms | 500 | Time-based flush interval (ms) |
| Max retry attempts | 3 | Per-batch retry before nack |
| Retry backoff | 200ms, 400ms, 800ms | Exponential backoff |

## Server-v3 Configuration

| Parameter | Value |
|-----------|-------|
| Server port | 8080 |
| JVM heap | -Xmx512m |
| WebSocket path | /chat/{roomId} |
| Session write safety | `synchronized(session)` |

## Client-part2 Configuration

| Parameter | Value |
|-----------|-------|
| Total messages | 500,000 (baseline) / 1,000,000 (stress) |
| Warmup threads | 32 |
| Warmup messages | 32,000 (1,000 per thread) |
| Main phase threads | 32 |
| Queue capacity | 10,000 (ArrayBlockingQueue) |
| Rooms | 20 |
| Message pool size | 50 |
| ACK timeout | 5 seconds |
| Max retries | 5 |
