#!/bin/bash
# Setup server-v2 on Amazon Linux 2023 EC2 instance
# Run as: sudo bash setup-server.sh <RABBITMQ_PRIVATE_IP> [SERVER_PORT]

set -e

RABBITMQ_HOST=${1:-"localhost"}
SERVER_PORT=${2:-"8080"}

echo "=== Installing Java 17 ==="
yum install -y java-17-amazon-corretto

echo "=== Verifying Java ==="
java -version

echo ""
echo "=== Setup Complete ==="
echo "To deploy and run:"
echo "  scp server-v2-1.0-SNAPSHOT.jar ec2-user@<THIS_IP>:~/"
echo "  nohup java -jar server-v2-1.0-SNAPSHOT.jar \\"
echo "    --server.port=${SERVER_PORT} \\"
echo "    --rabbitmq.host=${RABBITMQ_HOST} \\"
echo "    --server.id=\$(hostname) &"
echo ""
echo "Health check: curl http://localhost:${SERVER_PORT}/health"
