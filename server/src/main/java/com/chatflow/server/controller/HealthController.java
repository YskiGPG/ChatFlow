package com.chatflow.server.controller;

import com.chatflow.server.session.RoomSessionManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class HealthController {

    private final RoomSessionManager sessionManager;

    public HealthController(RoomSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "timestamp", Instant.now().toString(),
                "connections", sessionManager.getTotalConnections(),
                "rooms", sessionManager.getRoomCount()
        );
    }
}
