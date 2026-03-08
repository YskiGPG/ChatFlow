#!/bin/bash
# Monitor RabbitMQ queue depths and rates
# Usage: bash monitor-queues.sh <RABBITMQ_HOST> [interval_seconds]
#
# Requires: curl, jq
# RabbitMQ management plugin must be enabled (port 15672)

RABBITMQ_HOST=${1:-"localhost"}
INTERVAL=${2:-5}
USER="admin"
PASS="admin123"
BASE_URL="http://${RABBITMQ_HOST}:15672/api"

echo "=== ChatFlow Queue Monitor ==="
echo "RabbitMQ: $RABBITMQ_HOST"
echo "Interval: ${INTERVAL}s"
echo ""

while true; do
    TIMESTAMP=$(date '+%H:%M:%S')

    # Get all queues
    QUEUES=$(curl -s -u "$USER:$PASS" "$BASE_URL/queues" 2>/dev/null)

    if [ $? -ne 0 ] || [ -z "$QUEUES" ]; then
        echo "[$TIMESTAMP] ERROR: Cannot reach RabbitMQ management API"
        sleep $INTERVAL
        continue
    fi

    TOTAL_MESSAGES=$(echo "$QUEUES" | jq '[.[].messages] | add // 0')
    TOTAL_PUBLISH=$(echo "$QUEUES" | jq '[.[].message_stats.publish_details.rate // 0] | add')
    TOTAL_DELIVER=$(echo "$QUEUES" | jq '[.[].message_stats.deliver_get_details.rate // 0] | add')
    NUM_QUEUES=$(echo "$QUEUES" | jq 'length')
    NUM_CONSUMERS=$(echo "$QUEUES" | jq '[.[].consumers] | add // 0')

    printf "[%s] queues=%s depth=%s publish=%.0f/s consume=%.0f/s consumers=%s\n" \
        "$TIMESTAMP" "$NUM_QUEUES" "$TOTAL_MESSAGES" \
        "$TOTAL_PUBLISH" "$TOTAL_DELIVER" "$NUM_CONSUMERS"

    # Per-queue detail if depth > 0
    echo "$QUEUES" | jq -r '.[] | select(.messages > 0) |
        "         \(.name): depth=\(.messages) consumers=\(.consumers)"' 2>/dev/null

    sleep $INTERVAL
done
