package com.chatflow.server.repository;

import com.chatflow.server.model.MessageRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class MessageRepositoryImpl implements MessageRepository {

    private final JdbcTemplate jdbc;

    public MessageRepositoryImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<MessageRecord> findByRoomAndTimeRange(String roomId, String start, String end) {
        String sql = """
            SELECT message_id, room_id, user_id, username, message, message_type,
                   timestamp, server_id, client_ip
            FROM messages
            WHERE room_id = ? AND timestamp BETWEEN ? AND ?
            ORDER BY timestamp ASC
            """;
        return jdbc.query(sql, ROW_MAPPER, roomId, start, end);
    }

    @Override
    public List<MessageRecord> findByUserAndTimeRange(String userId, String start, String end) {
        String sql = """
            SELECT message_id, room_id, user_id, username, message, message_type,
                   timestamp, server_id, client_ip
            FROM messages
            WHERE user_id = ? AND timestamp BETWEEN ? AND ?
            ORDER BY timestamp ASC
            """;
        return jdbc.query(sql, ROW_MAPPER, userId, start, end);
    }

    @Override
    public long countActiveUsers(String start, String end) {
        String sql = """
            SELECT COUNT(DISTINCT user_id)
            FROM messages
            WHERE timestamp BETWEEN ? AND ?
            """;
        Long result = jdbc.queryForObject(sql, Long.class, start, end);
        return result != null ? result : 0L;
    }

    @Override
    public List<String> findRoomsByUser(String userId) {
        String sql = """
            SELECT DISTINCT room_id
            FROM messages
            WHERE user_id = ?
            ORDER BY room_id ASC
            """;
        return jdbc.queryForList(sql, String.class, userId);
    }

    @Override
    public List<Map<String, Object>> getThroughputStats() {
        String sql = """
            SELECT
                DATE_FORMAT(timestamp, '%Y-%m-%d %H:%i:00') AS minute_bucket,
                COUNT(*) AS message_count
            FROM messages
            GROUP BY minute_bucket
            ORDER BY minute_bucket DESC
            LIMIT 60
            """;
        return jdbc.queryForList(sql);
    }

    @Override
    public List<Map<String, Object>> getTopUsers(int n) {
        String sql = """
            SELECT user_id, username, COUNT(*) AS message_count
            FROM messages
            GROUP BY user_id, username
            ORDER BY message_count DESC
            LIMIT ?
            """;
        return jdbc.queryForList(sql, n);
    }

    @Override
    public List<Map<String, Object>> getTopRooms(int n) {
        String sql = """
            SELECT room_id, COUNT(*) AS message_count, COUNT(DISTINCT user_id) AS unique_users
            FROM messages
            GROUP BY room_id
            ORDER BY message_count DESC
            LIMIT ?
            """;
        return jdbc.queryForList(sql, n);
    }

    @Override
    public List<Map<String, Object>> getUserPatterns() {
        String sql = """
            SELECT
                user_id,
                username,
                COUNT(DISTINCT room_id)            AS rooms_visited,
                COUNT(*)                           AS total_messages,
                MIN(timestamp)                     AS first_seen,
                MAX(timestamp)                     AS last_seen
            FROM messages
            GROUP BY user_id, username
            ORDER BY total_messages DESC
            LIMIT 100
            """;
        return jdbc.queryForList(sql);
    }

    private static final RowMapper<MessageRecord> ROW_MAPPER = (rs, rowNum) -> {
        MessageRecord r = new MessageRecord();
        r.setMessageId(rs.getString("message_id"));
        r.setRoomId(rs.getString("room_id"));
        r.setUserId(rs.getString("user_id"));
        r.setUsername(rs.getString("username"));
        r.setMessage(rs.getString("message"));
        r.setMessageType(rs.getString("message_type"));
        r.setTimestamp(rs.getString("timestamp"));
        r.setServerId(rs.getString("server_id"));
        r.setClientIp(rs.getString("client_ip"));
        return r;
    };
}
