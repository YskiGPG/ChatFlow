package com.chatflow.consumer.db;

import com.chatflow.consumer.model.QueueMessage;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MessageBatchWriterTest {

    private static HikariDataSource dataSource;
    private static DatabaseManager dbManager;
    private MessageBatchWriter writer;

    @BeforeAll
    static void setupDatabase() throws Exception {
        HikariConfig config = new HikariConfig();
        // H2 in MySQL compatibility mode
        config.setJdbcUrl("jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(4);
        dataSource = new HikariDataSource(config);
        dbManager = new DatabaseManager(dataSource);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS messages (
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
    }

    @BeforeEach
    void setup() throws Exception {
        writer = new MessageBatchWriter(dbManager);
        // Clear table before each test
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM messages");
        }
    }

    @AfterAll
    static void teardown() {
        if (dataSource != null) dataSource.close();
    }

    // --- Tests ---

    @Test
    void writeBatch_insertsAllMessages() throws Exception {
        List<QueueMessage> batch = buildMessages(5, "msg-");
        int inserted = writer.writeBatch(batch);
        assertEquals(5, inserted);
        assertEquals(5, countRows());
    }

    @Test
    void writeBatch_duplicatesSkipped_insertIgnore() throws Exception {
        List<QueueMessage> batch = buildMessages(3, "dup-");
        writer.writeBatch(batch);

        // Re-submit same batch — INSERT IGNORE should not throw and DB stays at 3 rows
        assertDoesNotThrow(() -> writer.writeBatch(batch));
        assertEquals(3, countRows()); // still only 3 rows — no duplicates
    }

    @Test
    void writeBatch_partialDuplicates() throws Exception {
        List<QueueMessage> first = buildMessages(3, "partial-");
        writer.writeBatch(first);

        // 2 new + 1 duplicate
        List<QueueMessage> second = buildMessages(2, "new-");
        second.add(first.get(0)); // duplicate
        assertDoesNotThrow(() -> writer.writeBatch(second));

        // DB should have 3 original + 2 new = 5 (duplicate skipped)
        assertEquals(5, countRows());
    }

    @Test
    void writeBatch_emptyList_returnsZero() throws Exception {
        int inserted = writer.writeBatch(new ArrayList<>());
        assertEquals(0, inserted);
        assertEquals(0, countRows());
    }

    @Test
    void writeBatch_nullList_returnsZero() throws Exception {
        int inserted = writer.writeBatch(null);
        assertEquals(0, inserted);
    }

    @Test
    void writeBatch_nullTimestamp_usesCurrentTime() throws Exception {
        QueueMessage msg = buildMessage("null-ts-1");
        msg.setTimestamp(null);
        int inserted = writer.writeBatch(List.of(msg));
        assertEquals(1, inserted);
    }

    // --- Helpers ---

    private List<QueueMessage> buildMessages(int count, String prefix) {
        List<QueueMessage> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(buildMessage(prefix + i));
        }
        return list;
    }

    private QueueMessage buildMessage(String id) {
        QueueMessage m = new QueueMessage();
        m.setMessageId(id);
        m.setRoomId("room1");
        m.setUserId("user1");
        m.setUsername("testUser");
        m.setMessage("hello world");
        m.setMessageType("TEXT");
        m.setTimestamp("2024-01-15T10:30:00.000Z");
        m.setServerId("server-1");
        m.setClientIp("10.0.0.1");
        return m;
    }

    private int countRows() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM messages")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
