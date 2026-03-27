package com.chatflow.consumer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Standalone consumer application.
 *
 * In the ChatFlow architecture, the primary consumer is embedded in each
 * server-v2 instance (see server-v2/consumer/MessageConsumer.java).
 * This standalone consumer can be used for:
 * - Monitoring message flow
 * - Running additional consumer capacity on separate EC2
 * - Testing queue behavior
 *
 * Usage: java -jar consumer.jar <rabbitmq-host> [consumer-threads]
 */
public class ConsumerApp {

    private static final Logger log = LoggerFactory.getLogger(ConsumerApp.class);
    private static final String EXCHANGE_NAME = "chat.exchange";
    private static final Gson gson = new Gson();

    // Metrics
    private static final AtomicLong totalConsumed = new AtomicLong(0);
    private static final ConcurrentHashMap<String, AtomicLong> roomCounts = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException, TimeoutException, InterruptedException {
        String host = args.length > 0 ? args[0] : "localhost";
        int numThreads = args.length > 1 ? Integer.parseInt(args[1]) : 10;

        System.out.println("ChatFlow Standalone Consumer");
        System.out.println("============================");
        System.out.printf("RabbitMQ host: %s%n", host);
        System.out.printf("Consumer threads: %d%n", numThreads);

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setAutomaticRecoveryEnabled(true);
        Connection connection = factory.newConnection();

        // Declare exchange (idempotent)
        Channel setupChannel = connection.createChannel();
        setupChannel.exchangeDeclare(EXCHANGE_NAME, "topic", true);

        // Start consumer threads, each with its own channel and queue
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            Channel channel = connection.createChannel();
            channel.basicQos(64);

            // Each thread gets its own exclusive queue
            String queueName = "consumer-standalone-" + threadId;
            channel.queueDeclare(queueName, false, false, true, null);
            channel.queueBind(queueName, EXCHANGE_NAME, "room.*");

            DeliverCallback callback = (consumerTag, delivery) -> {
                try {
                    String json = new String(delivery.getBody());
                    JsonObject msg = gson.fromJson(json, JsonObject.class);
                    String roomId = msg.has("roomId") ? msg.get("roomId").getAsString() : "unknown";

                    totalConsumed.incrementAndGet();
                    roomCounts.computeIfAbsent(roomId, k -> new AtomicLong(0)).incrementAndGet();

                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                } catch (Exception e) {
                    log.error("Error processing: {}", e.getMessage());
                    channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
                }
            };

            channel.basicConsume(queueName, false, callback, consumerTag -> {});
            log.info("Consumer thread {} started on queue {}", threadId, queueName);
        }

        setupChannel.close();

        // Metrics reporting loop
        System.out.println("\nConsumer running. Press Ctrl+C to stop.\n");
        while (true) {
            Thread.sleep(10_000);
            long total = totalConsumed.get();
            System.out.printf("[Metrics] Total consumed: %d | Rooms: %s%n", total, roomCounts);
        }
    }
}
