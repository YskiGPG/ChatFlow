# ChatFlow - Distributed Chat System

CS6650 Assignments 1–3: WebSocket Chat Server, RabbitMQ Message Pipeline, and MySQL Persistence

## Architecture

```
Client (local) → Server-v3 (EC2-1) → RabbitMQ (EC2-1) → Consumer-v3 (EC2-2) → MySQL (EC2-2)
                      ↑                                                            ↑
                      └── Metrics API reads from ──────────────────────────────────┘
```

## Prerequisites

- Java 17+
- Maven 3.8+
- RabbitMQ 3.x (on server host)
- MySQL 8.0 (on consumer host)

## Project Structure

```
chatflow/
├── server/          # A1: Spring Boot WebSocket server
├── server-v2/       # A2: Server + RabbitMQ publishing
├── server-v3/       # A3: Server + RabbitMQ + Metrics REST API (MySQL read)
├── consumer/        # A2: RabbitMQ consumer (in-memory)
├── consumer-v3/     # A3: RabbitMQ consumer + MySQL batch writes
├── client-part1/    # A1: Basic load testing client
├── client-part2/    # A2/A3: Client with latency analysis + Metrics API caller
├── database/        # A3: MySQL schema and setup scripts
└── results/         # Test results, charts, reports
```

## Build

```bash
# Build all modules
mvn clean package -DskipTests

# Build specific module
mvn clean package -pl server-v3 -DskipTests
```

## Database Setup (EC2-2)

```bash
# Run the setup script on the MySQL host
cd database
chmod +x setup.sh
./setup.sh
```

This creates the `chatflow` database, `messages` table, and indexes. See `database/schema.sql` for the full schema.

## Run

### Server-v3 (EC2-1)

```bash
java -Xmx512m -jar server-v3-1.0-SNAPSHOT.jar \
  --spring.config.location=./application.properties
```

Server starts on port 8080:
- WebSocket: `ws://<host>:8080/chat/{roomId}`
- Health check: `http://<host>:8080/health`
- Metrics API: `http://<host>:8080/api/metrics/...`

### Consumer-v3 (EC2-2)

```bash
java -jar consumer-v3-1.0-SNAPSHOT.jar \
  rabbitmq.host=<EC2-1-private-ip> rabbitmq.username=admin rabbitmq.password=admin123 \
  mysql.host=localhost mysql.port=3306 mysql.database=chatflow \
  mysql.username=chatflow mysql.password=chatflow123 \
  batch.size=1000 flush.interval.ms=500 consumer.threads=10 writer.threads=4
```

### Client (local)

```bash
# 500K message test
java -jar client-part2/target/client-part2-1.0-SNAPSHOT.jar \
  500000 "ws://<server-ip>:8080/chat/" "http://<server-ip>:8080"

# 1M stress test
java -jar client-part2/target/client-part2-1.0-SNAPSHOT.jar \
  1000000 "ws://<server-ip>:8080/chat/" "http://<server-ip>:8080"
```

## Metrics API Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /api/metrics/rooms/{roomId}/messages?start=&end=` | Messages in a room within time range |
| `GET /api/metrics/users/{userId}/messages?start=&end=` | Messages by a user within time range |
| `GET /api/metrics/active-users?start=&end=` | Count of distinct active users |
| `GET /api/metrics/users/{userId}/rooms` | Rooms a user has participated in |
| `GET /api/metrics/analytics/throughput` | Per-minute message throughput (last 60 min) |
| `GET /api/metrics/analytics/top-users?n=N` | Top N users by message count |
| `GET /api/metrics/analytics/top-rooms?n=N` | Top N rooms by message count |
| `GET /api/metrics/analytics/user-patterns` | User behavior patterns |

## Quick Verify

```bash
# Install wscat
npm install -g wscat

# Connect to room 1
wscat -c ws://localhost:8080/chat/1

# Send a message
{"userId":"1","username":"testuser","message":"hello","timestamp":"2024-01-01T00:00:00Z","messageType":"TEXT"}
```
