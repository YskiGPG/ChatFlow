package com.chatflow.server.model;

public class ServerResponse {
    private String status;
    private String serverTimestamp;
    private ChatMessage originalMessage;
    private String error;

    public static ServerResponse success(ChatMessage message, String serverTimestamp) {
        ServerResponse response = new ServerResponse();
        response.status = "OK";
        response.serverTimestamp = serverTimestamp;
        response.originalMessage = message;
        return response;
    }

    public static ServerResponse error(String errorMessage) {
        ServerResponse response = new ServerResponse();
        response.status = "ERROR";
        response.error = errorMessage;
        return response;
    }

    public String getStatus() { return status; }
    public String getServerTimestamp() { return serverTimestamp; }
    public ChatMessage getOriginalMessage() { return originalMessage; }
    public String getError() { return error; }
}
