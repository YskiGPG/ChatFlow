package com.chatflow.server.model;

import java.util.UUID;

public class QueueMessage {
    private String messageId;
    private String roomId;
    private String userId;
    private String username;
    private String message;
    private String timestamp;
    private String messageType;
    private String serverId;
    private String clientIp;

    public static QueueMessage fromChatMessage(ChatMessage chatMessage, String roomId, String serverId, String clientIp) {
        QueueMessage qm = new QueueMessage();
        qm.messageId = UUID.randomUUID().toString();
        qm.roomId = roomId;
        qm.userId = chatMessage.getUserId();
        qm.username = chatMessage.getUsername();
        qm.message = chatMessage.getMessage();
        qm.timestamp = chatMessage.getTimestamp();
        qm.messageType = chatMessage.getMessageType();
        qm.serverId = serverId;
        qm.clientIp = clientIp;
        return qm;
    }

    public String getMessageId() { return messageId; }
    public String getRoomId() { return roomId; }
    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getMessage() { return message; }
    public String getTimestamp() { return timestamp; }
    public String getMessageType() { return messageType; }
    public String getServerId() { return serverId; }
    public String getClientIp() { return clientIp; }
}
