# Database Design Document — ChatFlow Assignment 3

## 1. Database Choice: MySQL 8.0

MySQL was chosen for the following reasons:

- **Relational query support**: The Metrics API requires complex queries — filtering by room, user, time range, aggregations (COUNT, GROUP BY), and multi-column ordering. SQL handles these natively without application-level processing.
- **Batch INSERT performance**: MySQL's multi-row INSERT and InnoDB buffer pool are well-suited for high-throughput batch writes from the consumer pipeline. With `INSERT IGNORE` and a UNIQUE constraint on `message_id`, writes are idempotent — duplicate messages from RabbitMQ redelivery are silently skipped.
- **ACID guarantees**: InnoDB provides transactional integrity, ensuring partial batch failures don't leave orphan rows.
- **Ecosystem maturity**: JDBC driver, HikariCP connection pooling, and Spring JdbcTemplate provide production-grade tooling with minimal boilerplate.

Alternatives considered:
- **MongoDB**: Flexible schema but weaker for the multi-dimensional aggregation queries the Metrics API requires (e.g., throughput-per-minute bucketing, top-N with GROUP BY).
- **Redis**: Fast but not designed for persistent, queryable storage at this scale.

## 2. Schema

```sql
CREATE TABLE messages (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id      VARCHAR(36)  NOT NULL UNIQUE,
    room_id         VARCHAR(20)  NOT NULL,
    user_id         VARCHAR(20)  NOT NULL,
    username        VARCHAR(20)  NOT NULL,
    message         VARCHAR(500) NOT NULL,
    message_type    ENUM('TEXT', 'JOIN', 'LEAVE') NOT NULL,
    timestamp       DATETIME(3)  NOT NULL,
    server_id       VARCHAR(50),
    client_ip       VARCHAR(45),
    created_at      DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

| Column | Type | Purpose |
|--------|------|---------|
| `id` | BIGINT AUTO_INCREMENT | Surrogate primary key for InnoDB clustered index |
| `message_id` | VARCHAR(36) UNIQUE | UUID from client — idempotency key for `INSERT IGNORE` |
| `room_id` | VARCHAR(20) | Chat room identifier, used in room-based queries |
| `user_id` | VARCHAR(20) | Sender identifier, used in user-based queries |
| `username` | VARCHAR(20) | Display name |
| `message` | VARCHAR(500) | Message body |
| `message_type` | ENUM | TEXT (90%), JOIN (5%), LEAVE (5%) |
| `timestamp` | DATETIME(3) | Client-side timestamp with millisecond precision |
| `server_id` | VARCHAR(50) | Which server instance processed the message |
| `client_ip` | VARCHAR(45) | Sender IP (supports IPv6 length) |
| `created_at` | DATETIME(3) | DB-side insertion timestamp |

Character set is `utf8mb4` to support full Unicode including emoji.

## 3. Indexing Strategy

Three secondary indexes were created to accelerate the Metrics API query patterns:

| Index | Columns | Supports |
|-------|---------|----------|
| `idx_room_time` | `(room_id, timestamp)` | `GET /rooms/{roomId}/messages?start=&end=` — composite index enables range scan on timestamp within a specific room |
| `idx_user_time` | `(user_id, timestamp)` | `GET /users/{userId}/messages?start=&end=` — same pattern for per-user queries |
| `idx_timestamp` | `(timestamp)` | `GET /active-users`, throughput analytics — time-range scans across all rooms/users |

**Design rationale**:
- Composite indexes `(partition_key, range_key)` allow MySQL to seek directly to the partition (room or user) and then scan only the relevant time range, avoiding full table scans.
- The standalone `timestamp` index serves cross-cutting queries (active users, throughput bucketing) that don't filter by room or user.
- `message_id` UNIQUE constraint doubles as an index for duplicate detection during `INSERT IGNORE`.

**Indexes NOT added** (and why):
- No index on `message_type`: only 3 values, low cardinality — a full scan within the already-filtered result set is cheaper than maintaining an additional index.
- No index on `username`: all user queries use `user_id`, not `username`.

## 4. Scaling Considerations

**Current capacity** (single MySQL instance on t3.small):
- 500K messages stored successfully with ~37 batch writes/sec (batch.size=1000)
- Table size ~50MB per 500K rows — fits entirely in InnoDB buffer pool

**Vertical scaling** (first step):
- Upgrade to t3.medium/large for more RAM → larger buffer pool → more data cached in memory
- Increase `innodb_buffer_pool_size` to 70-80% of available RAM
- Enable `innodb_flush_log_at_trx_commit=2` for write-heavy workloads (trade durability for throughput)

**Horizontal scaling** (if needed):
- **Read replicas**: The Metrics API is read-only on the server side — point it at a MySQL read replica to offload analytical queries from the write-primary
- **Partitioning**: RANGE partition on `timestamp` (e.g., by day/week) to speed up time-range queries and enable efficient partition pruning/dropping for data retention
- **Sharding by room_id**: If room count grows significantly, shard by `room_id` hash — each room's messages stay co-located for efficient queries

**Data retention**: For a production system, implement TTL-based cleanup (e.g., `DELETE FROM messages WHERE timestamp < NOW() - INTERVAL 30 DAY`) or use partition dropping for zero-cost data expiration.
