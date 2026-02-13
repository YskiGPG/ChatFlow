package com.chatflow.client.sender;

import com.chatflow.client.config.ClientConfig;
import com.chatflow.client.connection.ConnectionManager;
import com.chatflow.client.connection.ConnectionManager.ChatWebSocketClient;
import com.chatflow.client.metrics.BasicMetrics;
import com.chatflow.client.retry.RetryHandler;
import com.chatflow.client.retry.RetryHandler.SendResult;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class MessageSender implements Runnable {

    private final BlockingQueue<String> queue;
    private final int messageCount;
    private final int assignedRoomId;
    private final ConnectionManager connectionManager;
    private final RetryHandler retryHandler;
    private final BasicMetrics metrics;

    public MessageSender(BlockingQueue<String> queue, int messageCount, int assignedRoomId,
                         ConnectionManager connectionManager, RetryHandler retryHandler,
                         BasicMetrics metrics) {
        this.queue = queue;
        this.messageCount = messageCount;
        this.assignedRoomId = assignedRoomId;
        this.connectionManager = connectionManager;
        this.retryHandler = retryHandler;
        this.metrics = metrics;
    }

    @Override
    public void run() {
        ChatWebSocketClient client = null;

        try {
            // Connect once to assigned room
            client = connectionManager.connect(assignedRoomId);

            for (int i = 0; i < messageCount; i++) {
                String messageJson = queue.poll(5, TimeUnit.SECONDS);
                if (messageJson == null) {
                    continue;
                }

                // Reconnect if connection dropped
                if (!client.isOpen()) {
                    client = connectionManager.reconnect(client, assignedRoomId);
                }

                // Send with retry
                SendResult result = retryHandler.sendWithRetry(client, messageJson, assignedRoomId);
                client = result.getClient();

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
}
