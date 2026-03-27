#!/bin/bash
# Deploy and run server-v3 on an EC2 instance
# Usage: bash deploy-server.sh <EC2_IP> <PEM_FILE> <RABBITMQ_IP> <MYSQL_IP> [SERVER_ID]

set -e

EC2_IP=$1
PEM_FILE=$2
RABBITMQ_IP=$3
MYSQL_IP=$4
SERVER_ID=${5:-"server-$(echo $EC2_IP | tr '.' '-')"}

if [ -z "$EC2_IP" ] || [ -z "$PEM_FILE" ] || [ -z "$RABBITMQ_IP" ] || [ -z "$MYSQL_IP" ]; then
    echo "Usage: $0 <EC2_IP> <PEM_FILE> <RABBITMQ_IP> <MYSQL_IP> [SERVER_ID]"
    exit 1
fi

JAR="server-v3/target/server-v3-1.0-SNAPSHOT.jar"

if [ ! -f "$JAR" ]; then
    echo "Building server-v3..."
    mvn clean package -pl server-v3 -DskipTests
fi

echo "=== Deploying to $EC2_IP (id=$SERVER_ID) ==="
scp -i "$PEM_FILE" "$JAR" "ec2-user@${EC2_IP}:~/server-v3.jar"

echo "=== Starting server-v3 ==="
ssh -i "$PEM_FILE" "ec2-user@${EC2_IP}" << REMOTE
    pkill -f server-v3.jar || true
    sleep 2

    nohup java -jar server-v3.jar \
        --server.port=8080 \
        --rabbitmq.host=${RABBITMQ_IP} \
        --server.id=${SERVER_ID} \
        --spring.datasource.url=jdbc:mysql://${MYSQL_IP}:3306/chatflow?useSSL=false\&allowPublicKeyRetrieval=true \
        --spring.datasource.username=chatflow \
        --spring.datasource.password=chatflow123 \
        > server.log 2>&1 &

    sleep 3
    curl -s http://localhost:8080/health || echo "Health check failed — check server.log"
REMOTE

echo "=== Server-v3 deployed: $EC2_IP ==="
