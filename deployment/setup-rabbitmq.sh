#!/bin/bash
# Setup RabbitMQ on Amazon Linux 2023 EC2 instance
# Run as: sudo bash setup-rabbitmq.sh

set -e

echo "=== Installing Erlang and RabbitMQ ==="
yum install -y erlang rabbitmq-server

echo "=== Starting RabbitMQ ==="
systemctl enable rabbitmq-server
systemctl start rabbitmq-server

echo "=== Enabling Management Plugin ==="
rabbitmq-plugins enable rabbitmq_management

echo "=== Creating admin user ==="
rabbitmqctl add_user admin admin123
rabbitmqctl set_user_tags admin administrator
rabbitmqctl set_permissions -p / admin ".*" ".*" ".*"

echo "=== RabbitMQ Status ==="
rabbitmqctl status

echo ""
echo "=== Setup Complete ==="
echo "AMQP port:       5672"
echo "Management UI:   http://<EC2_IP>:15672"
echo "Username/Pass:   admin / admin123"
echo ""
echo "Required Security Group rules:"
echo "  - Port 5672  (AMQP)      from Server instances"
echo "  - Port 15672 (Management) from your IP"
echo "  - Port 22    (SSH)        from your IP"
