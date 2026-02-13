package com.chatflow.client.metrics;

public class LatencyRecord {
    private final long sendTimestamp;   // epoch ms
    private final String messageType;
    private final long latencyMs;
    private final String statusCode;    // OK or ERROR
    private final int roomId;

    public LatencyRecord(long sendTimestamp, String messageType, long latencyMs, String statusCode, int roomId) {
        this.sendTimestamp = sendTimestamp;
        this.messageType = messageType;
        this.latencyMs = latencyMs;
        this.statusCode = statusCode;
        this.roomId = roomId;
    }

    public long getSendTimestamp() { return sendTimestamp; }
    public String getMessageType() { return messageType; }
    public long getLatencyMs() { return latencyMs; }
    public String getStatusCode() { return statusCode; }
    public int getRoomId() { return roomId; }

    public String toCsvLine() {
        return sendTimestamp + "," + messageType + "," + latencyMs + "," + statusCode + "," + roomId;
    }

    public static String csvHeader() {
        return "timestamp,messageType,latency,statusCode,roomId";
    }
}
