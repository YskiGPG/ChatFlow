package com.chatflow.consumer.db;

import com.chatflow.consumer.model.QueueMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.List;

/**
 * Executes a JDBC batch INSERT IGNORE into the messages table.
 * INSERT IGNORE silently skips rows whose message_id already exists (idempotent).
 */
public class MessageBatchWriter {

    private static final Logger log = LoggerFactory.getLogger(MessageBatchWriter.class);

    private static final String INSERT_SQL =
        "INSERT IGNORE INTO messages " +
        "(message_id, room_id, user_id, username, message, message_type, timestamp, server_id, client_ip) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final DatabaseManager dbManager;

    public MessageBatchWriter(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Batch-inserts a list of messages.
     *
     * @return number of rows actually inserted (duplicates excluded via INSERT IGNORE)
     * @throws SQLException if the batch execution fails
     */
    public int writeBatch(List<QueueMessage> messages) throws SQLException {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }

        int inserted = 0;
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {

            for (QueueMessage msg : messages) {
                ps.setString(1, msg.getMessageId());
                ps.setString(2, msg.getRoomId());
                ps.setString(3, msg.getUserId());
                ps.setString(4, msg.getUsername());
                ps.setString(5, msg.getMessage());
                ps.setString(6, msg.getMessageType() != null ? msg.getMessageType() : "TEXT");
                ps.setTimestamp(7, parseTimestamp(msg.getTimestamp()));
                ps.setString(8, msg.getServerId());
                ps.setString(9, msg.getClientIp());
                ps.addBatch();
            }

            int[] results = ps.executeBatch();
            for (int r : results) {
                // Statement.SUCCESS_NO_INFO (-2) counts as success for INSERT IGNORE
                if (r >= 0 || r == Statement.SUCCESS_NO_INFO) {
                    inserted++;
                }
            }
        }

        log.debug("Batch write: {} submitted, {} inserted (duplicates skipped)", messages.size(), inserted);
        return inserted;
    }

    private Timestamp parseTimestamp(String ts) {
        if (ts == null || ts.isEmpty()) {
            return new Timestamp(System.currentTimeMillis());
        }
        try {
            // Handle ISO-8601: "2024-01-15T10:30:00.123Z" or millis string
            if (ts.contains("T")) {
                return Timestamp.from(java.time.Instant.parse(ts));
            } else {
                return new Timestamp(Long.parseLong(ts));
            }
        } catch (Exception e) {
            log.warn("Failed to parse timestamp '{}', using current time", ts);
            return new Timestamp(System.currentTimeMillis());
        }
    }
}
