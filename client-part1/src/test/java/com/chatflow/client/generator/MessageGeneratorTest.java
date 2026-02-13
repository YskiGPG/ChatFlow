package com.chatflow.client.generator;

import com.chatflow.client.config.ClientConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

class MessageGeneratorTest {

    private final Gson gson = new Gson();

    @Test
    void generatesCorrectNumberOfMessages() throws InterruptedException {
        int count = 100;
        BlockingQueue<String> queue = new ArrayBlockingQueue<>(count);
        MessageGenerator generator = new MessageGenerator(queue, count);

        Thread t = new Thread(generator);
        t.start();
        t.join();

        assertEquals(count, queue.size());
    }

    @Test
    void messagesHaveRequiredFields() throws InterruptedException {
        BlockingQueue<String> queue = new ArrayBlockingQueue<>(10);
        MessageGenerator generator = new MessageGenerator(queue, 1);

        Thread t = new Thread(generator);
        t.start();
        t.join();

        String json = queue.poll();
        assertNotNull(json);

        JsonObject obj = gson.fromJson(json, JsonObject.class);
        assertTrue(obj.has("userId"));
        assertTrue(obj.has("username"));
        assertTrue(obj.has("message"));
        assertTrue(obj.has("timestamp"));
        assertTrue(obj.has("messageType"));
        assertTrue(obj.has("roomId"));
    }

    @Test
    void userIdInValidRange() throws InterruptedException {
        BlockingQueue<String> queue = new ArrayBlockingQueue<>(1000);
        MessageGenerator generator = new MessageGenerator(queue, 1000);

        Thread t = new Thread(generator);
        t.start();
        t.join();

        for (String json : queue) {
            JsonObject obj = gson.fromJson(json, JsonObject.class);
            int userId = Integer.parseInt(obj.get("userId").getAsString());
            assertTrue(userId >= 1 && userId <= 100000, "userId out of range: " + userId);
        }
    }

    @Test
    void roomIdInValidRange() throws InterruptedException {
        BlockingQueue<String> queue = new ArrayBlockingQueue<>(1000);
        MessageGenerator generator = new MessageGenerator(queue, 1000);

        Thread t = new Thread(generator);
        t.start();
        t.join();

        for (String json : queue) {
            JsonObject obj = gson.fromJson(json, JsonObject.class);
            int roomId = obj.get("roomId").getAsInt();
            assertTrue(roomId >= 1 && roomId <= ClientConfig.NUM_ROOMS, "roomId out of range: " + roomId);
        }
    }

    @Test
    void messageTypeDistributionApproximate() throws InterruptedException {
        int count = 10_000;
        BlockingQueue<String> queue = new ArrayBlockingQueue<>(count);
        MessageGenerator generator = new MessageGenerator(queue, count);

        Thread t = new Thread(generator);
        t.start();
        t.join();

        int textCount = 0, joinCount = 0, leaveCount = 0;
        for (String json : queue) {
            JsonObject obj = gson.fromJson(json, JsonObject.class);
            String type = obj.get("messageType").getAsString();
            switch (type) {
                case "TEXT" -> textCount++;
                case "JOIN" -> joinCount++;
                case "LEAVE" -> leaveCount++;
                default -> fail("Unknown messageType: " + type);
            }
        }

        // Allow 2% tolerance
        assertTrue(textCount > count * 0.88, "TEXT ratio too low: " + textCount);
        assertTrue(joinCount > count * 0.03, "JOIN ratio too low: " + joinCount);
        assertTrue(leaveCount > count * 0.03, "LEAVE ratio too low: " + leaveCount);
    }
}
