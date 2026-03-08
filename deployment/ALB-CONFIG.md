# AWS Application Load Balancer Configuration

## Architecture
```
Client -> ALB (:80) -> [Server1:8080, Server2:8080, Server3:8080, Server4:8080]
                         └── all publish to RabbitMQ
                         └── all consume from own exclusive queue
```

## Step-by-Step Setup

### 1. Create Target Group
- Name: `chatflow-servers`
- Target type: Instances
- Protocol: HTTP, Port: 8080
- Health check:
  - Path: `/health`
  - Interval: 30s
  - Timeout: 5s
  - Healthy threshold: 2
  - Unhealthy threshold: 3
- Register server EC2 instances

### 2. Create ALB
- Name: `chatflow-alb`
- Scheme: Internet-facing
- Listeners: HTTP :80
- Availability Zones: select all in us-west-2
- Security Group: allow inbound 80 from 0.0.0.0/0

### 3. Configure Sticky Sessions
- Target Group → Attributes → Edit
- Stickiness: Enabled
- Type: Application-based cookie
- Duration: 86400 (1 day)
- Cookie name: `CHATFLOWSESSION`

> Sticky sessions are REQUIRED for WebSocket. Without them,
> the HTTP upgrade request and subsequent frames may hit different servers.

### 4. Idle Timeout
- ALB → Attributes → Edit
- Idle timeout: 120 seconds (default is 60)

## Listener Rule
Default rule: forward to `chatflow-servers` target group

## Test Configurations

| Test | Servers in Target Group | Client Threads |
|------|------------------------|----------------|
| Baseline | 1 | 128 |
| 2-server | 2 | 256 |
| 4-server | 4 | 512 |

## Verify Distribution
```bash
# Check each server's connection count
for IP in $SERVER1_IP $SERVER2_IP $SERVER3_IP $SERVER4_IP; do
    echo "$IP: $(curl -s http://$IP:8080/health | jq .connections)"
done
```
