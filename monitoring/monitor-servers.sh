#!/bin/bash
# Monitor all server instances health
# Usage: bash monitor-servers.sh <server1_ip> [server2_ip] [server3_ip] [server4_ip]
#
# Polls /health endpoint of each server

INTERVAL=5
SERVERS=("$@")

if [ ${#SERVERS[@]} -eq 0 ]; then
    echo "Usage: bash monitor-servers.sh <server1_ip> [server2_ip] ..."
    exit 1
fi

echo "=== ChatFlow Server Monitor ==="
echo "Servers: ${SERVERS[*]}"
echo "Interval: ${INTERVAL}s"
echo ""
echo "Time       | Server          | Conns | Rooms | Consumed | Broadcast | Skipped"
echo "-----------+-----------------+-------+-------+----------+-----------+--------"

while true; do
    TIMESTAMP=$(date '+%H:%M:%S')

    for IP in "${SERVERS[@]}"; do
        HEALTH=$(curl -s --connect-timeout 2 "http://${IP}:8080/health" 2>/dev/null)

        if [ $? -ne 0 ] || [ -z "$HEALTH" ]; then
            printf "%s | %-15s | DOWN\n" "$TIMESTAMP" "$IP"
            continue
        fi

        CONNS=$(echo "$HEALTH" | jq '.connections // 0')
        ROOMS=$(echo "$HEALTH" | jq '.rooms // 0')
        CONSUMED=$(echo "$HEALTH" | jq '.messagesConsumed // 0')
        BROADCAST=$(echo "$HEALTH" | jq '.messagesBroadcast // 0')
        SKIPPED=$(echo "$HEALTH" | jq '.messagesSkipped // 0')

        printf "%s | %-15s | %5s | %5s | %8s | %9s | %s\n" \
            "$TIMESTAMP" "$IP" "$CONNS" "$ROOMS" "$CONSUMED" "$BROADCAST" "$SKIPPED"
    done

    echo ""
    sleep $INTERVAL
done
