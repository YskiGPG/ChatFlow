package com.chatflow.client.generator;

import com.chatflow.client.config.ClientConfig;
import com.chatflow.client.model.ChatMessage;
import com.google.gson.Gson;

import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadLocalRandom;

public class MessageGenerator implements Runnable {

    private final BlockingQueue<String> queue;
    private final int totalMessages;
    private final Gson gson = new Gson();

    private static final String[] MESSAGE_POOL = new String[ClientConfig.MESSAGE_POOL_SIZE];

    static {
        for (int i = 0; i < ClientConfig.MESSAGE_POOL_SIZE; i++) {
            MESSAGE_POOL[i] = "Sample chat message number " + (i + 1) + " for load testing";
        }
    }

    public MessageGenerator(BlockingQueue<String> queue, int totalMessages) {
        this.queue = queue;
        this.totalMessages = totalMessages;
    }

    @Override
    public void run() {
        try {
            for (int i = 0; i < totalMessages; i++) {
                String json = generateMessage();
                queue.put(json); // blocks if queue is full
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String generateMessage() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int userId = random.nextInt(1, 100001);
        String username = "user" + userId;
        String message = MESSAGE_POOL[random.nextInt(MESSAGE_POOL.length)];
        int roomId = random.nextInt(1, ClientConfig.NUM_ROOMS + 1);
        String messageType = pickMessageType(random);
        String timestamp = Instant.now().toString();

        ChatMessage chatMessage = new ChatMessage(
                String.valueOf(userId), username, message, timestamp, messageType, roomId
        );
        return gson.toJson(chatMessage);
    }

    private String pickMessageType(ThreadLocalRandom random) {
        int roll = random.nextInt(100);
        if (roll < 90) return "TEXT";
        if (roll < 95) return "JOIN";
        return "LEAVE";
    }
}
