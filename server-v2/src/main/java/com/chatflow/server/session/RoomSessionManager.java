package com.chatflow.server.session;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RoomSessionManager {

    private final ConcurrentHashMap<String, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();

    public void addSession(String roomId, WebSocketSession session) {
        rooms.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void removeSession(String roomId, WebSocketSession session) {
        Set<WebSocketSession> sessions = rooms.get(roomId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                rooms.remove(roomId);
            }
        }
    }

    public Set<WebSocketSession> getSessions(String roomId) {
        return rooms.getOrDefault(roomId, Collections.emptySet());
    }

    public int getTotalConnections() {
        return rooms.values().stream().mapToInt(Set::size).sum();
    }

    public int getRoomCount() {
        return rooms.size();
    }
}
