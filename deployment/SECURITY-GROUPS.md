# EC2 Security Group Configuration for A3

## Instances

| Instance | Role | Ports open inbound |
|----------|------|--------------------|
| EC2-1 | Server-v3 | 8080 (HTTP/WS from ALB), 22 (SSH) |
| EC2-2 | RabbitMQ | 5672 (AMQP from EC2-1 and EC2-3), 15672 (management, your IP), 22 (SSH) |
| EC2-3 | Consumer-v3 + MySQL | 3306 (MySQL from EC2-1 private IP only), 22 (SSH) |

## MySQL Port 3306 Rule (EC2-3 Security Group)

Add an **inbound rule** on the EC2-3 security group:

| Type | Protocol | Port | Source |
|------|----------|------|--------|
| Custom TCP | TCP | 3306 | EC2-1 private IP/32 (e.g. 10.0.1.10/32) |

**Important**: Do NOT open 3306 to 0.0.0.0/0. Only allow the server-v3 private IP.

## AWS CLI Commands

Replace `<sg-consumer-id>` with EC2-3's security group ID and `<ec2-1-private-ip>` with EC2-1's private IP.

```bash
# Allow server-v3 (EC2-1) to reach MySQL on EC2-3
aws ec2 authorize-security-group-ingress \
    --group-id <sg-consumer-id> \
    --protocol tcp \
    --port 3306 \
    --cidr <ec2-1-private-ip>/32

# Allow consumer-v3 (EC2-3) to reach RabbitMQ on EC2-2
aws ec2 authorize-security-group-ingress \
    --group-id <sg-rabbitmq-id> \
    --protocol tcp \
    --port 5672 \
    --cidr <ec2-3-private-ip>/32
```

## Verification

```bash
# From EC2-1: test MySQL reachability
nc -zv <ec2-3-private-ip> 3306

# From EC2-3: test RabbitMQ reachability
nc -zv <ec2-2-private-ip> 5672
```
