package com.chatflow.server.controller;

import com.chatflow.server.consumer.MessageConsumer;
import com.chatflow.server.session.RoomSessionManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class HealthController {

    private final RoomSessionManager sessionManager;
    private final MessageConsumer consumer;
    private final String serverId;

    public HealthController(RoomSessionManager sessionManager,
                            MessageConsumer consumer,
                            @Qualifier("serverId") String serverId) {
        this.sessionManager = sessionManager;
        this.consumer = consumer;
        this.serverId = serverId;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "serverId", serverId,
                "timestamp", Instant.now().toString(),
                "connections", sessionManager.getTotalConnections(),
                "rooms", sessionManager.getRoomCount(),
                "messagesConsumed", consumer.getMessagesConsumed(),
                "messagesBroadcast", consumer.getMessagesBroadcast(),
                "messagesSkipped", consumer.getMessagesSkipped()
        );
    }
}
