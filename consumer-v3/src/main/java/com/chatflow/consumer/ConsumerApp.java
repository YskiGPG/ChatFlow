package com.chatflow.consumer;

import com.chatflow.consumer.buffer.MessageBuffer;
import com.chatflow.consumer.config.ConsumerConfig;
import com.chatflow.consumer.db.DatabaseManager;
import com.chatflow.consumer.db.MessageBatchWriter;
import com.chatflow.consumer.metrics.ConsumerMetrics;
import com.chatflow.consumer.model.QueueMessage;
import com.chatflow.consumer.writer.DatabaseWriterPool;
import com.google.gson.Gson;
import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Consumer-v3 main entry point.
 *
 * Pipeline:
 *   RabbitMQ → ConsumerApp (N threads) → MessageBuffer → DatabaseWriterPool (M threads) → MySQL
 *
 * Usage:
 *   java -jar consumer-v3.jar [key=value ...]
 *   e.g. java -jar consumer-v3.jar rabbitmq.host=10.0.0.1 mysql.host=10.0.0.2
 */
public class ConsumerApp {

    private static final Logger log = LoggerFactory.getLogger(ConsumerApp.class);

    private static final String EXCHANGE_NAME = "chat.exchange";
    private static final String QUEUE_NAME    = "consumer-v3-db-queue";
    private static final String ROUTING_KEY   = "room.*";

    private static final Gson gson = new Gson();

    public static void main(String[] args) throws Exception {
        ConsumerConfig config = ConsumerConfig.load(args);
        log.info("Starting Consumer-v3 with {}", config);

        // --- Infrastructure ---
        DatabaseManager  dbManager   = new DatabaseManager(config);
        MessageBatchWriter batchWriter = new MessageBatchWriter(dbManager);
        ConsumerMetrics  metrics     = new ConsumerMetrics();
        DatabaseWriterPool writerPool = new DatabaseWriterPool(config.getWriterThreads(), batchWriter, metrics);

        MessageBuffer buffer = new MessageBuffer(
            config.getBatchSize(),
            config.getFlushIntervalMs(),
            batch -> {
                metrics.incrementBatches();
                writerPool.submit(batch);
            }
        );

        // --- RabbitMQ connection ---
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(config.getRabbitHost());
        factory.setPort(config.getRabbitPort());
        factory.setUsername(config.getRabbitUser());
        factory.setPassword(config.getRabbitPassword());
        factory.setVirtualHost(config.getRabbitVhost());
        factory.setAutomaticRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(5_000);

        Connection connection = factory.newConnection("consumer-v3");

        // Declare durable queue (idempotent)
        Channel setupChannel = connection.createChannel();
        setupChannel.exchangeDeclare(EXCHANGE_NAME, "topic", true);
        setupChannel.queueDeclare(QUEUE_NAME, true, false, false, null);
        setupChannel.queueBind(QUEUE_NAME, EXCHANGE_NAME, ROUTING_KEY);
        setupChannel.close();
        log.info("Queue '{}' declared and bound to exchange '{}'", QUEUE_NAME, EXCHANGE_NAME);

        // --- Start consumer threads ---
        List<Channel> channels = new ArrayList<>();
        for (int i = 0; i < config.getConsumerThreads(); i++) {
            Channel channel = connection.createChannel();
            channel.basicQos(config.getPrefetchCount());
            channels.add(channel);

            DeliverCallback callback = (consumerTag, delivery) -> {
                try {
                    String json = new String(delivery.getBody());
                    QueueMessage msg = gson.fromJson(json, QueueMessage.class);
                    msg.setDeliveryTag(delivery.getEnvelope().getDeliveryTag());
                    msg.setChannel(channel);

                    metrics.incrementConsumed();
                    buffer.add(msg);
                } catch (Exception e) {
                    log.error("Failed to parse message: {}", e.getMessage());
                    channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
                }
            };

            channel.basicConsume(QUEUE_NAME, false, callback, consumerTag -> {});
            log.info("Consumer thread {} started", i);
        }

        // --- Graceful shutdown hook ---
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received — draining pipeline...");

            // 1. Stop timed flushes and drain buffer
            buffer.shutdown();
            List<QueueMessage> remaining = buffer.drainAll();
            if (!remaining.isEmpty()) {
                log.info("Flushing {} remaining messages from buffer", remaining.size());
                writerPool.writeSync(remaining);
            }

            // 2. Wait for writer pool to finish in-flight batches
            writerPool.shutdown();

            // 3. Close HikariCP pool
            dbManager.shutdown();

            // 4. Close RabbitMQ channels and connection
            for (Channel ch : channels) {
                try { if (ch.isOpen()) ch.close(); } catch (Exception ignored) {}
            }
            try { connection.close(); } catch (Exception ignored) {}

            // 5. Final metrics snapshot
            metrics.shutdown();
            log.info("Consumer-v3 shutdown complete.");
        }, "shutdown-hook"));

        log.info("Consumer-v3 running. Consuming from '{}'. Press Ctrl+C to stop.", QUEUE_NAME);
    }
}
