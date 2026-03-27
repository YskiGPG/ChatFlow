package com.chatflow.server.model;

/**
 * Represents a row from the messages table, used in Metrics API responses.
 */
public class MessageRecord {
    private String messageId;
    private String roomId;
    private String userId;
    private String username;
    private String message;
    private String messageType;
    private String timestamp;
    private String serverId;
    private String clientIp;

    public MessageRecord() {}

    public String getMessageId()   { return messageId; }
    public void setMessageId(String v)   { this.messageId = v; }

    public String getRoomId()      { return roomId; }
    public void setRoomId(String v)      { this.roomId = v; }

    public String getUserId()      { return userId; }
    public void setUserId(String v)      { this.userId = v; }

    public String getUsername()    { return username; }
    public void setUsername(String v)    { this.username = v; }

    public String getMessage()     { return message; }
    public void setMessage(String v)     { this.message = v; }

    public String getMessageType() { return messageType; }
    public void setMessageType(String v) { this.messageType = v; }

    public String getTimestamp()   { return timestamp; }
    public void setTimestamp(String v)   { this.timestamp = v; }

    public String getServerId()    { return serverId; }
    public void setServerId(String v)    { this.serverId = v; }

    public String getClientIp()    { return clientIp; }
    public void setClientIp(String v)    { this.clientIp = v; }
}
