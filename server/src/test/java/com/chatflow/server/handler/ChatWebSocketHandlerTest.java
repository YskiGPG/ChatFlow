package com.chatflow.server.handler;

import com.chatflow.server.model.ServerResponse;
import com.chatflow.server.session.RoomSessionManager;
import com.chatflow.server.validation.MessageValidator;
import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChatWebSocketHandlerTest {

    private ChatWebSocketHandler handler;
    private RoomSessionManager sessionManager;
    private Gson gson = new Gson();

    @BeforeEach
    void setUp() {
        sessionManager = new RoomSessionManager();
        MessageValidator validator = new MessageValidator();
        handler = new ChatWebSocketHandler(validator, sessionManager);
    }

    private StubWebSocketSession createSession(String id, String roomId) {
        return new StubWebSocketSession(id, URI.create("ws://localhost:8080/chat/" + roomId));
    }

    @Test
    void connectionEstablished_addsSessionToRoom() throws Exception {
        StubWebSocketSession session = createSession("s1", "1");
        handler.afterConnectionEstablished(session);

        assertEquals(1, sessionManager.getTotalConnections());
        assertTrue(sessionManager.getSessions("1").contains(session));
    }

    @Test
    void connectionClosed_removesSessionFromRoom() throws Exception {
        StubWebSocketSession session = createSession("s1", "1");
        handler.afterConnectionEstablished(session);
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        assertEquals(0, sessionManager.getTotalConnections());
    }

    @Test
    void validMessage_returnsOkResponse() throws Exception {
        StubWebSocketSession session = createSession("s1", "1");
        handler.afterConnectionEstablished(session);

        String validJson = """
                {"userId":"1","username":"testuser","message":"hello","timestamp":"2024-01-01T00:00:00Z","messageType":"TEXT"}
                """;
        handler.handleTextMessage(session, new TextMessage(validJson));

        assertEquals(1, session.getSentMessages().size());
        ServerResponse response = gson.fromJson(session.getSentMessages().get(0), ServerResponse.class);
        assertEquals("OK", response.getStatus());
        assertNotNull(response.getServerTimestamp());
        assertEquals("testuser", response.getOriginalMessage().getUsername());
    }

    @Test
    void invalidJson_returnsErrorResponse() throws Exception {
        StubWebSocketSession session = createSession("s1", "1");
        handler.afterConnectionEstablished(session);

        handler.handleTextMessage(session, new TextMessage("not json"));

        assertEquals(1, session.getSentMessages().size());
        ServerResponse response = gson.fromJson(session.getSentMessages().get(0), ServerResponse.class);
        assertEquals("ERROR", response.getStatus());
        assertTrue(response.getError().contains("Invalid JSON"));
    }

    @Test
    void invalidUserId_returnsErrorResponse() throws Exception {
        StubWebSocketSession session = createSession("s1", "1");
        handler.afterConnectionEstablished(session);

        String invalidJson = """
                {"userId":"0","username":"testuser","message":"hello","timestamp":"2024-01-01T00:00:00Z","messageType":"TEXT"}
                """;
        handler.handleTextMessage(session, new TextMessage(invalidJson));

        ServerResponse response = gson.fromJson(session.getSentMessages().get(0), ServerResponse.class);
        assertEquals("ERROR", response.getStatus());
        assertTrue(response.getError().contains("userId"));
    }

    @Test
    void multipleRooms_sessionsIsolated() throws Exception {
        StubWebSocketSession session1 = createSession("s1", "1");
        StubWebSocketSession session2 = createSession("s2", "2");

        handler.afterConnectionEstablished(session1);
        handler.afterConnectionEstablished(session2);

        assertEquals(2, sessionManager.getTotalConnections());
        assertEquals(1, sessionManager.getSessions("1").size());
        assertEquals(1, sessionManager.getSessions("2").size());
    }

    /**
     * Hand-written stub replacing Mockito mock.
     * Captures sent messages for assertion.
     */
    static class StubWebSocketSession implements WebSocketSession {
        private final String id;
        private final URI uri;
        private boolean open = true;
        private final List<String> sentMessages = new ArrayList<>();

        StubWebSocketSession(String id, URI uri) {
            this.id = id;
            this.uri = uri;
        }

        List<String> getSentMessages() { return sentMessages; }

        @Override public String getId() { return id; }
        @Override public URI getUri() { return uri; }
        @Override public boolean isOpen() { return open; }

        @Override
        public void sendMessage(org.springframework.web.socket.WebSocketMessage<?> message) throws IOException {
            sentMessages.add(message.getPayload().toString());
        }

        @Override public void close() { open = false; }
        @Override public void close(CloseStatus status) { open = false; }

        // --- unused interface methods ---
        @Override public HttpHeaders getHandshakeHeaders() { return new HttpHeaders(); }
        @Override public Map<String, Object> getAttributes() { return Collections.emptyMap(); }
        @Override public Principal getPrincipal() { return null; }
        @Override public InetSocketAddress getLocalAddress() { return null; }
        @Override public InetSocketAddress getRemoteAddress() { return null; }
        @Override public String getAcceptedProtocol() { return null; }
        @Override public void setTextMessageSizeLimit(int i) {}
        @Override public int getTextMessageSizeLimit() { return 0; }
        @Override public void setBinaryMessageSizeLimit(int i) {}
        @Override public int getBinaryMessageSizeLimit() { return 0; }
        @Override public List<WebSocketExtension> getExtensions() { return Collections.emptyList(); }
    }
}
