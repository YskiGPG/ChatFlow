package com.chatflow.server.integration;

import com.chatflow.server.ChatServerApplication;
import com.chatflow.server.model.ServerResponse;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = ChatServerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class WebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    private final Gson gson = new Gson();

    private static final String VALID_MESSAGE = """
            {"userId":"1","username":"testuser","message":"hello world","timestamp":"2024-01-01T00:00:00Z","messageType":"TEXT"}
            """;

    /**
     * Helper: connect to a room and collect responses in a queue.
     */
    private WebSocketSession connectToRoom(String roomId, BlockingQueue<String> responses) throws Exception {
        StandardWebSocketClient client = new StandardWebSocketClient();
        URI uri = new URI("ws://localhost:" + port + "/chat/" + roomId);

        return client.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                responses.offer(message.getPayload());
            }
        }, new WebSocketHttpHeaders(), uri).get(5, TimeUnit.SECONDS);
    }

    @Test
    void validMessage_getsEchoedBack() throws Exception {
        BlockingQueue<String> responses = new ArrayBlockingQueue<>(10);
        WebSocketSession session = connectToRoom("1", responses);

        session.sendMessage(new TextMessage(VALID_MESSAGE));

        String response = responses.poll(5, TimeUnit.SECONDS);
        assertNotNull(response, "Should receive a response");

        ServerResponse serverResponse = gson.fromJson(response, ServerResponse.class);
        assertEquals("OK", serverResponse.getStatus());
        assertNotNull(serverResponse.getServerTimestamp());
        assertEquals("testuser", serverResponse.getOriginalMessage().getUsername());
        assertEquals("hello world", serverResponse.getOriginalMessage().getMessage());

        session.close();
    }

    @Test
    void invalidMessage_getsErrorResponse() throws Exception {
        BlockingQueue<String> responses = new ArrayBlockingQueue<>(10);
        WebSocketSession session = connectToRoom("1", responses);

        session.sendMessage(new TextMessage("not valid json at all"));

        String response = responses.poll(5, TimeUnit.SECONDS);
        assertNotNull(response);

        ServerResponse serverResponse = gson.fromJson(response, ServerResponse.class);
        assertEquals("ERROR", serverResponse.getStatus());
        assertNotNull(serverResponse.getError());

        session.close();
    }

    @Test
    void invalidUserId_getsErrorResponse() throws Exception {
        BlockingQueue<String> responses = new ArrayBlockingQueue<>(10);
        WebSocketSession session = connectToRoom("1", responses);

        String invalidMsg = """
                {"userId":"999999","username":"testuser","message":"hello","timestamp":"2024-01-01T00:00:00Z","messageType":"TEXT"}
                """;
        session.sendMessage(new TextMessage(invalidMsg));

        String response = responses.poll(5, TimeUnit.SECONDS);
        ServerResponse serverResponse = gson.fromJson(response, ServerResponse.class);
        assertEquals("ERROR", serverResponse.getStatus());
        assertTrue(serverResponse.getError().contains("userId"));

        session.close();
    }

    @Test
    void multipleMessages_allGetResponses() throws Exception {
        BlockingQueue<String> responses = new ArrayBlockingQueue<>(100);
        WebSocketSession session = connectToRoom("1", responses);

        int count = 10;
        for (int i = 0; i < count; i++) {
            String msg = String.format(
                    """
                    {"userId":"%d","username":"user%d","message":"msg %d","timestamp":"2024-01-01T00:00:00Z","messageType":"TEXT"}
                    """, i + 1, i + 1, i);
            session.sendMessage(new TextMessage(msg));
        }

        for (int i = 0; i < count; i++) {
            String response = responses.poll(5, TimeUnit.SECONDS);
            assertNotNull(response, "Should receive response for message " + i);
            ServerResponse sr = gson.fromJson(response, ServerResponse.class);
            assertEquals("OK", sr.getStatus());
        }

        session.close();
    }

    @Test
    void differentRooms_bothWork() throws Exception {
        BlockingQueue<String> responses1 = new ArrayBlockingQueue<>(10);
        BlockingQueue<String> responses2 = new ArrayBlockingQueue<>(10);

        WebSocketSession session1 = connectToRoom("1", responses1);
        WebSocketSession session2 = connectToRoom("2", responses2);

        session1.sendMessage(new TextMessage(VALID_MESSAGE));
        session2.sendMessage(new TextMessage(VALID_MESSAGE));

        assertNotNull(responses1.poll(5, TimeUnit.SECONDS), "Room 1 should respond");
        assertNotNull(responses2.poll(5, TimeUnit.SECONDS), "Room 2 should respond");

        session1.close();
        session2.close();
    }

    @Test
    void joinAndLeaveMessageTypes_accepted() throws Exception {
        BlockingQueue<String> responses = new ArrayBlockingQueue<>(10);
        WebSocketSession session = connectToRoom("1", responses);

        String joinMsg = """
                {"userId":"1","username":"testuser","message":"joining","timestamp":"2024-01-01T00:00:00Z","messageType":"JOIN"}
                """;
        session.sendMessage(new TextMessage(joinMsg));
        ServerResponse joinResp = gson.fromJson(responses.poll(5, TimeUnit.SECONDS), ServerResponse.class);
        assertEquals("OK", joinResp.getStatus());

        String leaveMsg = """
                {"userId":"1","username":"testuser","message":"leaving","timestamp":"2024-01-01T00:00:00Z","messageType":"LEAVE"}
                """;
        session.sendMessage(new TextMessage(leaveMsg));
        ServerResponse leaveResp = gson.fromJson(responses.poll(5, TimeUnit.SECONDS), ServerResponse.class);
        assertEquals("OK", leaveResp.getStatus());

        session.close();
    }
}
