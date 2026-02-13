package com.chatflow.client.sender;

import com.chatflow.client.connection.ConnectionManager;
import com.chatflow.client.connection.ConnectionManager.ChatWebSocketClient;
import com.chatflow.client.metrics.BasicMetrics;
import com.chatflow.client.metrics.LatencyCollector;
import com.chatflow.client.metrics.LatencyRecord;
import com.chatflow.client.retry.RetryHandler;
import com.chatflow.client.retry.RetryHandler.SendResult;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class MessageSender implements Runnable {

    private final BlockingQueue<String> queue;
    private final int messageCount;
    private final int assignedRoomId;
    private final ConnectionManager connectionManager;
    private final RetryHandler retryHandler;
    private final BasicMetrics metrics;
    private final LatencyCollector latencyCollector;
    private final Gson gson = new Gson();

    public MessageSender(BlockingQueue<String> queue, int messageCount, int assignedRoomId,
                         ConnectionManager connectionManager, RetryHandler retryHandler,
                         BasicMetrics metrics, LatencyCollector latencyCollector) {
        this.queue = queue;
        this.messageCount = messageCount;
        this.assignedRoomId = assignedRoomId;
        this.connectionManager = connectionManager;
        this.retryHandler = retryHandler;
        this.metrics = metrics;
        this.latencyCollector = latencyCollector;
    }

    @Override
    public void run() {
        ChatWebSocketClient client = null;

        try {
            client = connectionManager.connect(assignedRoomId);

            for (int i = 0; i < messageCount; i++) {
                String messageJson = queue.poll(5, TimeUnit.SECONDS);
                if (messageJson == null) {
                    continue;
                }

                if (!client.isOpen()) {
                    client = connectionManager.reconnect(client, assignedRoomId);
                }

                // Extract messageType for the latency record
                String messageType = extractMessageType(messageJson);

                // Record send time
                long sendTime = System.currentTimeMillis();

                SendResult result = retryHandler.sendWithRetry(client, messageJson, assignedRoomId);
                client = result.getClient();

                // Record ack time
                long ackTime = System.currentTimeMillis();
                long latency = ackTime - sendTime;
                String statusCode = result.isSuccess() ? "OK" : "FAIL";

                // Record latency
                latencyCollector.record(new LatencyRecord(
                        sendTime, messageType, latency, statusCode, assignedRoomId));

                if (result.isSuccess()) {
                    metrics.incrementSuccess();
                } else {
                    metrics.incrementFailed();
                }
            }
        } catch (Exception e) {
            System.err.println("Sender error: " + e.getMessage());
        } finally {
            if (client != null && client.isOpen()) {
                client.close();
            }
        }
    }

    private String extractMessageType(String json) {
        try {
            JsonObject obj = gson.fromJson(json, JsonObject.class);
            return obj.get("messageType").getAsString();
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
}
