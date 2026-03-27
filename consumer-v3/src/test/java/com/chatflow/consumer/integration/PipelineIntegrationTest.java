package com.chatflow.consumer.integration;

import com.chatflow.consumer.buffer.MessageBuffer;
import com.chatflow.consumer.db.DatabaseManager;
import com.chatflow.consumer.db.MessageBatchWriter;
import com.chatflow.consumer.model.QueueMessage;
import com.chatflow.consumer.writer.DatabaseWriterPool;
import com.github.fridujo.rabbitmq.mock.MockConnectionFactory;
import com.google.gson.Gson;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: wires MessageBuffer → DatabaseWriterPool → H2 (MySQL mode).
 * Uses rabbitmq-mock for RabbitMQ, H2 for MySQL.
 */
class PipelineIntegrationTest {

    private static final String EXCHANGE = "chat.exchange";
    private static final String QUEUE    = "test-db-queue";
    private static final Gson   gson     = new Gson();

    private HikariDataSource dataSource;
    private DatabaseManager  dbManager;
    private MessageBatchWriter batchWriter;
    private DatabaseWriterPool writerPool;
    private MessageBuffer      buffer;
    private com.rabbitmq.client.Connection rabbitConn;

    @BeforeEach
    void setUp() throws Exception {
        // H2 in MySQL mode
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:h2:mem:integration-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        hc.setUsername("sa");
        hc.setPassword("");
        hc.setMaximumPoolSize(5);
        dataSource = new HikariDataSource(hc);
        dbManager  = new DatabaseManager(dataSource);

        try (java.sql.Connection c = dataSource.getConnection();
             Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE messages (
                    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
                    message_id   VARCHAR(36)  NOT NULL UNIQUE,
                    room_id      VARCHAR(20)  NOT NULL,
                    user_id      VARCHAR(20)  NOT NULL,
                    username     VARCHAR(20)  NOT NULL,
                    message      VARCHAR(500) NOT NULL,
                    message_type VARCHAR(10)  NOT NULL,
                    timestamp    DATETIME(3)  NOT NULL,
                    server_id    VARCHAR(50),
                    client_ip    VARCHAR(45),
                    created_at   DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3)
                )
                """);
        }

        batchWriter = new MessageBatchWriter(dbManager);
        writerPool  = new DatabaseWriterPool(2, batchWriter, new com.chatflow.consumer.metrics.ConsumerMetrics());

        buffer = new MessageBuffer(50, 500, batch -> writerPool.submit(batch));

        // rabbitmq-mock
        MockConnectionFactory mockFactory = new MockConnectionFactory();
        rabbitConn = mockFactory.newConnection();
        Channel setup = rabbitConn.createChannel();
        setup.exchangeDeclare(EXCHANGE, "topic", true);
        setup.queueDeclare(QUEUE, true, false, false, null);
        setup.queueBind(QUEUE, EXCHANGE, "room.*");
        setup.close();
    }

    @AfterEach
    void tearDown() throws Exception {
        buffer.shutdown();
        writerPool.shutdown();
        dbManager.shutdown();
        try { rabbitConn.close(); } catch (Exception ignored) {}
    }

    @Test
    void publish100Messages_allPersistedInDB() throws Exception {
        int msgCount = 100;
        CountDownLatch latch = new CountDownLatch(1);
        AtomicLong consumed = new AtomicLong(0);

        // Start consumer
        Channel consumerChannel = rabbitConn.createChannel();
        consumerChannel.basicQos(64);

        DeliverCallback callback = (tag, delivery) -> {
            QueueMessage msg = gson.fromJson(new String(delivery.getBody()), QueueMessage.class);
            msg.setDeliveryTag(delivery.getEnvelope().getDeliveryTag());
            msg.setChannel(consumerChannel);
            buffer.add(msg);
            if (consumed.incrementAndGet() >= msgCount) latch.countDown();
        };
        consumerChannel.basicConsume(QUEUE, false, callback, t -> {});

        // Publish 100 messages
        Channel publishChannel = rabbitConn.createChannel();
        for (int i = 0; i < msgCount; i++) {
            String body = gson.toJson(buildMessage("msg-" + i));
            publishChannel.basicPublish(EXCHANGE, "room.1", null, body.getBytes());
        }
        publishChannel.close();

        // Wait for consumption
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Not all messages consumed in time");

        // Force final flush and wait for DB writes
        buffer.shutdown();
        List<QueueMessage> remaining = buffer.drainAll();
        if (!remaining.isEmpty()) writerPool.writeSync(remaining);
        writerPool.shutdown();

        // Allow async writes to complete
        Thread.sleep(500);

        assertEquals(msgCount, countRows());
    }

    @Test
    void duplicateMessages_onlyStoredOnce() throws Exception {
        List<QueueMessage> batch = new ArrayList<>();
        for (int i = 0; i < 10; i++) batch.add(buildMessage("dup-" + i));
        batchWriter.writeBatch(batch);
        batchWriter.writeBatch(batch); // same IDs — INSERT IGNORE skips

        assertEquals(10, countRows());
    }

    // --- Helpers ---

    private QueueMessage buildMessage(String id) {
        QueueMessage m = new QueueMessage();
        m.setMessageId(id);
        m.setRoomId("room1");
        m.setUserId("user1");
        m.setUsername("tester");
        m.setMessage("integration test message");
        m.setMessageType("TEXT");
        m.setTimestamp("2024-01-15T10:00:00.000Z");
        m.setServerId("srv-1");
        m.setClientIp("10.0.0.1");
        return m;
    }

    private int countRows() throws Exception {
        try (java.sql.Connection c = dataSource.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM messages")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
