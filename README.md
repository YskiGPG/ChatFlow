# ChatFlow - Distributed Chat System

CS6650 Assignment 1: WebSocket Chat Server and Multithreaded Client

## Prerequisites

- Java 17+
- Maven 3.8+
  title: ChatFlow Architecture
---

```mermaid
    ---
title: ChatFlow Architecture
---

graph TB
%% =========================
%% Server
%% =========================
subgraph Server["Server Module (Spring Boot :8080)"]
WC[WebSocketConfig<br/>/chat/roomId] --> Handler[ChatWebSocketHandler]
Handler --> Validator[MessageValidator]
Handler --> RSM[RoomSessionManager<br/>ConcurrentHashMap&lt;roomId, Set&lt;Session&gt;&gt;]
Handler --> Response[ServerResponse<br/>echo + serverTimestamp]
HC[HealthController<br/>GET /health]
end

%% =========================
%% Client
%% =========================
subgraph Client["Client Module"]
subgraph Producer["Producer (1 thread)"]
MG[MessageGenerator<br/>500K messages<br/>50 msg pool / 20 rooms<br/>90% TEXT 5% JOIN 5% LEAVE]
end

BQ[/"ArrayBlockingQueue<br/>(capacity: 10K)"/]

subgraph Consumers["Consumers (N threads)"]
MS1[MessageSender #1]
MS2[MessageSender #2]
MSN[MessageSender #N]
end

subgraph Support["Support Components"]
CM[ConnectionManager<br/>connect / reconnect]
RH[RetryHandler<br/>exponential backoff x5]
BM[BasicMetrics<br/>AtomicLong counters]
end

%% Producer -> Queue
MG -->|"queue.put()"| BQ

%% Queue -> Consumers
BQ -->|"queue.poll()"| MS1
BQ --> MS2
BQ --> MSN

%% Sender internal deps
MS1 --> CM
MS1 --> RH
MS1 --> BM
end

%% =========================
%% WebSocket Interaction
%% =========================
MS1 -->|"WebSocket<br/>send → wait ack → next"| Handler
MS2 -->|"WebSocket"| Handler
MSN -->|"WebSocket"| Handler

```

```mermaid
graph TB

    subgraph Phases["Execution Phases"]
        W["Warmup Phase<br/>32 threads × 1000 msgs"]
        M["Main Phase<br/>128 threads × ~3656 msgs"]
    end

    subgraph LittlesLaw["Little's Law: λ = L / W"]
        LL["L = 128 threads<br/>W = ~5ms RTT<br/><br/>λ = 128 / 0.005<br/>= 25,600 msg/sec<br/><br/>500K msgs ≈ 20 sec"]
    end

    W --> M
    M --> LL

```

```
chatflow/
├── server/          # Spring Boot WebSocket server
├── client-part1/    # Basic load testing client
├── client-part2/    # Client with performance analysis
└── results/         # Test results and analysis
```

## 
## Project Structure

```
chatflow/
├── server/          # Spring Boot WebSocket server
├── client-part1/    # Basic load testing client
├── client-part2/    # Client with performance analysis
└── results/         # Test results and analysis
```

## Build

```bash
# Build all modules
mvn clean package

# Build specific module
mvn clean package -pl server
```

## Run

### Server

```bash
cd server
mvn spring-boot:run
# Or after packaging:
java -jar target/server-1.0-SNAPSHOT.jar
```

Server starts on port 8080:
- WebSocket: `ws://localhost:8080/chat/{roomId}`
- Health check: `http://localhost:8080/health`

### Client Part 1

```bash
# Make sure server is running first
cd client-part1
mvn exec:java -Dexec.mainClass="com.chatflow.client.ClientApp"
# Or after packaging:
java -jar target/client-part1-1.0-SNAPSHOT.jar
```

### Client Part 2

```bash
cd client-part2
java -jar target/client-part2-1.0-SNAPSHOT.jar
```

## Test

```bash
# Run all tests
mvn test

# Run tests for specific module
mvn test -pl server
```

## Quick Verify with wscat

```bash
# Install wscat
npm install -g wscat

# Connect to room 1
wscat -c ws://localhost:8080/chat/1

# Send a message
{"userId":"1","username":"testuser","message":"hello","timestamp":"2024-01-01T00:00:00Z","messageType":"TEXT"}
```
