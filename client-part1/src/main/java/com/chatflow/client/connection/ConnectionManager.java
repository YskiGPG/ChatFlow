package com.chatflow.client.connection;

import com.chatflow.client.config.ClientConfig;
import com.chatflow.client.metrics.BasicMetrics;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ConnectionManager {

    private final BasicMetrics metrics;

    public ConnectionManager(BasicMetrics metrics) {
        this.metrics = metrics;
    }

    /**
     * Creates and connects a WebSocket client to the given room.
     * Blocks until the connection is established or timeout.
     */
    public ChatWebSocketClient connect(int roomId) throws Exception {
        URI uri = new URI(ClientConfig.SERVER_URI + roomId);
        ChatWebSocketClient client = new ChatWebSocketClient(uri);
        client.connectBlocking(10, TimeUnit.SECONDS);
        if (!client.isOpen()) {
            throw new RuntimeException("Failed to connect to " + uri);
        }
        metrics.incrementConnections();
        return client;
    }

    /**
     * Reconnect a closed client to the same room.
     */
    public ChatWebSocketClient reconnect(ChatWebSocketClient oldClient, int roomId) throws Exception {
        metrics.incrementReconnections();
        return connect(roomId);
    }

    public static class ChatWebSocketClient extends WebSocketClient {
        private volatile String lastResponse;
        private final CountDownLatch responseLatch = new CountDownLatch(1);
        private volatile CountDownLatch ackLatch;

        public ChatWebSocketClient(URI serverUri) {
            super(serverUri);
        }

        @Override
        public void onOpen(ServerHandshake handshake) {}

        @Override
        public void onMessage(String message) {
            lastResponse = message;
            if (ackLatch != null) {
                ackLatch.countDown();
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {}

        @Override
        public void onError(Exception ex) {}

        /**
         * Send a message and wait for the ack synchronously.
         * Returns the server response, or null on timeout.
         */
        public String sendAndWaitForAck(String message, long timeoutMs) throws InterruptedException {
            ackLatch = new CountDownLatch(1);
            lastResponse = null;
            send(message);
            boolean received = ackLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
            return received ? lastResponse : null;
        }
    }
}
