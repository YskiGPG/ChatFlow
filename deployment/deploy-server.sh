#!/bin/bash
# Deploy and run server-v2 on an EC2 instance
# Usage: bash deploy-server.sh <EC2_IP> <PEM_FILE> <RABBITMQ_IP> <SERVER_ID>

set -e

EC2_IP=$1
PEM_FILE=$2
RABBITMQ_IP=$3
SERVER_ID=${4:-"server-$(echo $EC2_IP | tr '.' '-')"}

JAR="server-v2/target/server-v2-1.0-SNAPSHOT.jar"

if [ ! -f "$JAR" ]; then
    echo "Building server-v2..."
    mvn clean package -pl server-v2 -DskipTests
fi

echo "=== Deploying to $EC2_IP (id=$SERVER_ID) ==="
scp -i "$PEM_FILE" "$JAR" "ec2-user@${EC2_IP}:~/server-v2.jar"

echo "=== Starting server ==="
ssh -i "$PEM_FILE" "ec2-user@${EC2_IP}" << REMOTE
    # Kill existing server if running
    pkill -f server-v2.jar || true
    sleep 2

    nohup java -jar server-v2.jar \
        --server.port=8080 \
        --rabbitmq.host=${RABBITMQ_IP} \
        --server.id=${SERVER_ID} \
        > server.log 2>&1 &

    sleep 3
    curl -s http://localhost:8080/health
REMOTE

echo "=== Server deployed: $EC2_IP ==="
