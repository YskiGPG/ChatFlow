package com.chatflow.server.consumer;

import com.chatflow.server.model.QueueMessage;
import com.chatflow.server.rabbitmq.ChannelPoolManager;
import com.chatflow.server.session.RoomSessionManager;
import com.google.gson.Gson;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class MessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(MessageConsumer.class);
    private final Gson gson = new Gson();
    private final ChannelPoolManager channelPool;
    private final RoomSessionManager sessionManager;
    private final String queueName;
    private final String serverId;

    @Value("${consumer.prefetch:64}")
    private int prefetchCount;

    // Deduplication: messageId -> timestamp
    private final ConcurrentHashMap<String, Long> seenMessages = new ConcurrentHashMap<>();
    private static final long DEDUP_TTL_MS = 60_000;

    // Metrics
    private final AtomicLong messagesConsumed = new AtomicLong(0);
    private final AtomicLong messagesBroadcast = new AtomicLong(0);
    private final AtomicLong messagesSkipped = new AtomicLong(0);

    public MessageConsumer(ChannelPoolManager channelPool,
                           RoomSessionManager sessionManager,
                           @Qualifier("exclusiveQueueName") String queueName,
                           @Qualifier("serverId") String serverId) {
        this.channelPool = channelPool;
        this.sessionManager = sessionManager;
        this.queueName = queueName;
        this.serverId = serverId;
    }

    @PostConstruct
    public void startConsuming() throws Exception {
        // Create a dedicated channel for consuming (not from pool)
        Channel consumerChannel = channelPool.getConnection().createChannel();
        consumerChannel.basicQos(prefetchCount);

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            try {
                String json = new String(delivery.getBody());
                QueueMessage message = gson.fromJson(json, QueueMessage.class);
                messagesConsumed.incrementAndGet();

                // Deduplication check
                if (isDuplicate(message.getMessageId())) {
                    messagesSkipped.incrementAndGet();
                    consumerChannel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                    return;
                }

                // Broadcast to local sessions for this room
                broadcastToRoom(message);

                // Manual ack after successful broadcast
                consumerChannel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            } catch (Exception e) {
                log.error("Error processing message: {}", e.getMessage());
                // Nack and requeue on failure
                consumerChannel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
            }
        };

        consumerChannel.basicConsume(queueName, false, deliverCallback, consumerTag -> {
            log.warn("Consumer cancelled: {}", consumerTag);
        });

        log.info("Consumer started: queue={}, serverId={}, prefetch={}", queueName, serverId, prefetchCount);

        // Start dedup cleanup thread
        startDedupCleanup();
    }

    private void broadcastToRoom(QueueMessage message) {
        Set<WebSocketSession> sessions = sessionManager.getSessions(message.getRoomId());
        if (sessions.isEmpty()) {
            return; // No local sessions for this room
        }

        String json = gson.toJson(message);
        TextMessage textMessage = new TextMessage(json);

        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(textMessage);
                    }
                    messagesBroadcast.incrementAndGet();
                } catch (IOException e) {
                    log.warn("Failed to send to session {}: {}", session.getId(), e.getMessage());
                }
            }
        }
    }

    private boolean isDuplicate(String messageId) {
        Long existing = seenMessages.putIfAbsent(messageId, System.currentTimeMillis());
        return existing != null;
    }

    private void startDedupCleanup() {
        Thread cleanupThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(30_000);
                    long cutoff = System.currentTimeMillis() - DEDUP_TTL_MS;
                    seenMessages.entrySet().removeIf(e -> e.getValue() < cutoff);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "dedup-cleanup");
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    // Metrics accessors
    public long getMessagesConsumed() { return messagesConsumed.get(); }
    public long getMessagesBroadcast() { return messagesBroadcast.get(); }
    public long getMessagesSkipped() { return messagesSkipped.get(); }
    public int getDeduplicationCacheSize() { return seenMessages.size(); }
}
