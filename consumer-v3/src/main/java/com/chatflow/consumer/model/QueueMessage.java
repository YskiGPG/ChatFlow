package com.chatflow.consumer.model;

/**
 * POJO matching the JSON published to RabbitMQ by server-v3.
 * deliveryTag is set after deserialization for RabbitMQ ack tracking.
 */
public class QueueMessage {

    private String messageId;
    private String roomId;
    private String userId;
    private String username;
    private String message;
    private String timestamp;   // ISO-8601 string from server
    private String messageType; // TEXT | JOIN | LEAVE
    private String serverId;
    private String clientIp;

    // Not in JSON — set by consumer thread after delivery
    private transient long deliveryTag;
    private transient com.rabbitmq.client.Channel channel;

    public QueueMessage() {}

    // --- Getters / Setters ---

    public String getMessageId()   { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getRoomId()      { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public String getUserId()      { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername()    { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getMessage()     { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getTimestamp()   { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    public String getServerId()    { return serverId; }
    public void setServerId(String serverId) { this.serverId = serverId; }

    public String getClientIp()    { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }

    public long getDeliveryTag()   { return deliveryTag; }
    public void setDeliveryTag(long deliveryTag) { this.deliveryTag = deliveryTag; }

    public com.rabbitmq.client.Channel getChannel() { return channel; }
    public void setChannel(com.rabbitmq.client.Channel channel) { this.channel = channel; }

    @Override
    public String toString() {
        return String.format("QueueMessage{id=%s, room=%s, user=%s, type=%s}",
            messageId, roomId, userId, messageType);
    }
}
