package com.chatflow.client.model;

import java.time.Instant;

public class ChatMessage {
    private String userId;
    private String username;
    private String message;
    private String timestamp;
    private String messageType;
    private int roomId;

    public ChatMessage(String userId, String username, String message, String timestamp, String messageType, int roomId) {
        this.userId = userId;
        this.username = username;
        this.message = message;
        this.timestamp = timestamp;
        this.messageType = messageType;
        this.roomId = roomId;
    }

    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getMessage() { return message; }
    public String getTimestamp() { return timestamp; }
    public String getMessageType() { return messageType; }
    public int getRoomId() { return roomId; }
}
