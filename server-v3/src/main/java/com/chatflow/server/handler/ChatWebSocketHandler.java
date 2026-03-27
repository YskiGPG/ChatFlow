package com.chatflow.server.handler;

import com.chatflow.server.model.ChatMessage;
import com.chatflow.server.model.QueueMessage;
import com.chatflow.server.model.ServerResponse;
import com.chatflow.server.rabbitmq.MessagePublisher;
import com.chatflow.server.session.RoomSessionManager;
import com.chatflow.server.validation.MessageValidator;
import com.chatflow.server.validation.MessageValidator.ValidationResult;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private final Gson gson = new Gson();
    private final MessageValidator validator;
    private final RoomSessionManager sessionManager;
    private final MessagePublisher publisher;
    private final String serverId;

    public ChatWebSocketHandler(MessageValidator validator,
                                RoomSessionManager sessionManager,
                                MessagePublisher publisher,
                                @Qualifier("serverId") String serverId) {
        this.validator = validator;
        this.sessionManager = sessionManager;
        this.publisher = publisher;
        this.serverId = serverId;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String roomId = extractRoomId(session);
        sessionManager.addSession(roomId, session);
        log.info("Connection established: session={}, room={}, server={}",
                session.getId(), roomId, serverId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) throws Exception {
        String payload = textMessage.getPayload();

        // Parse JSON
        ChatMessage chatMessage;
        try {
            chatMessage = gson.fromJson(payload, ChatMessage.class);
        } catch (JsonSyntaxException e) {
            ServerResponse errorResponse = ServerResponse.error("Invalid JSON format");
            session.sendMessage(new TextMessage(gson.toJson(errorResponse)));
            return;
        }

        // Validate
        ValidationResult result = validator.validate(chatMessage);
        if (!result.isValid()) {
            ServerResponse errorResponse = ServerResponse.error(result.getErrorMessage());
            session.sendMessage(new TextMessage(gson.toJson(errorResponse)));
            return;
        }

        // Publish to RabbitMQ instead of echoing
        String roomId = extractRoomId(session);
        String clientIp = session.getRemoteAddress() != null
                ? session.getRemoteAddress().getAddress().getHostAddress() : "unknown";
        QueueMessage queueMessage = QueueMessage.fromChatMessage(chatMessage, roomId, serverId, clientIp);

        try {
            publisher.publish(queueMessage);
        } catch (Exception e) {
            log.error("Failed to publish to RabbitMQ: {}", e.getMessage());
            ServerResponse errorResponse = ServerResponse.error("Server error: message not delivered");
            session.sendMessage(new TextMessage(gson.toJson(errorResponse)));
            return;
        }

        // Ack to sender
        ServerResponse successResponse = ServerResponse.success(chatMessage, Instant.now().toString());
        session.sendMessage(new TextMessage(gson.toJson(successResponse)));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String roomId = extractRoomId(session);
        sessionManager.removeSession(roomId, session);
        log.info("Connection closed: session={}, room={}, status={}", session.getId(), roomId, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("Transport error: session={}, error={}", session.getId(), exception.getMessage());
    }

    private String extractRoomId(WebSocketSession session) {
        String path = session.getUri().getPath();
        String[] parts = path.split("/");
        return parts.length >= 3 ? parts[2] : "default";
    }
}
