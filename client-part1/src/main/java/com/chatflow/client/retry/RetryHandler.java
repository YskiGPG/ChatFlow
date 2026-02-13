package com.chatflow.client.retry;

import com.chatflow.client.config.ClientConfig;
import com.chatflow.client.connection.ConnectionManager;
import com.chatflow.client.connection.ConnectionManager.ChatWebSocketClient;

public class RetryHandler {

    private final ConnectionManager connectionManager;

    public RetryHandler(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    /**
     * Sends a message with up to maxRetries attempts using exponential backoff.
     * Returns the server response on success, or null after all retries exhausted.
     * May reconnect the WebSocket if the connection is broken.
     */
    public SendResult sendWithRetry(ChatWebSocketClient client, String message, int roomId) {
        for (int attempt = 0; attempt < ClientConfig.MAX_RETRIES; attempt++) {
            try {
                if (!client.isOpen()) {
                    client = connectionManager.reconnect(client, roomId);
                }
                String response = client.sendAndWaitForAck(message, ClientConfig.ACK_TIMEOUT_MS);
                if (response != null) {
                    return new SendResult(true, response, client);
                }
            } catch (Exception e) {
                // Fall through to retry
            }

            // Exponential backoff
            try {
                Thread.sleep((long) Math.pow(2, attempt) * 100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new SendResult(false, null, client);
            }
        }
        return new SendResult(false, null, client);
    }

    public static class SendResult {
        private final boolean success;
        private final String response;
        private final ChatWebSocketClient client;

        public SendResult(boolean success, String response, ChatWebSocketClient client) {
            this.success = success;
            this.response = response;
            this.client = client;
        }

        public boolean isSuccess() { return success; }
        public String getResponse() { return response; }
        public ChatWebSocketClient getClient() { return client; }
    }
}
