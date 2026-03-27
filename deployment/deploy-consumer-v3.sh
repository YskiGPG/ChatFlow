#!/bin/bash
# Deploy and run consumer-v3 on EC2 (co-located with MySQL)
# Usage: bash deploy-consumer-v3.sh <EC2_IP> <PEM_FILE> <RABBITMQ_IP> [MYSQL_IP]
#   MYSQL_IP defaults to localhost (co-located deployment)

set -e

EC2_IP=$1
PEM_FILE=$2
RABBITMQ_IP=$3
MYSQL_IP=${4:-"localhost"}

if [ -z "$EC2_IP" ] || [ -z "$PEM_FILE" ] || [ -z "$RABBITMQ_IP" ]; then
    echo "Usage: $0 <EC2_IP> <PEM_FILE> <RABBITMQ_IP> [MYSQL_IP]"
    echo "  MYSQL_IP defaults to localhost (co-located MySQL)"
    exit 1
fi

JAR="consumer-v3/target/consumer-v3-1.0-SNAPSHOT.jar"

if [ ! -f "$JAR" ]; then
    echo "Building consumer-v3..."
    mvn clean package -pl consumer-v3 -DskipTests
fi

echo "=== Deploying consumer-v3 to $EC2_IP ==="
scp -i "$PEM_FILE" "$JAR" "ec2-user@${EC2_IP}:~/consumer-v3.jar"

echo "=== Starting consumer-v3 ==="
ssh -i "$PEM_FILE" "ec2-user@${EC2_IP}" << REMOTE
    pkill -f consumer-v3.jar || true
    sleep 2

    nohup java -jar consumer-v3.jar \
        rabbitmq.host=${RABBITMQ_IP} \
        mysql.host=${MYSQL_IP} \
        > consumer.log 2>&1 &

    sleep 3
    echo "consumer-v3 started. Tailing first 20 lines of log:"
    head -20 consumer.log || true
REMOTE

echo "=== Consumer-v3 deployed: $EC2_IP ==="
echo "To tail logs: ssh -i $PEM_FILE ec2-user@${EC2_IP} 'tail -f consumer.log'"
