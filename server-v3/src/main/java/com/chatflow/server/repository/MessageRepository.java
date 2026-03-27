package com.chatflow.server.repository;

import com.chatflow.server.model.MessageRecord;

import java.util.List;
import java.util.Map;

public interface MessageRepository {

    // Core queries
    List<MessageRecord> findByRoomAndTimeRange(String roomId, String start, String end);
    List<MessageRecord> findByUserAndTimeRange(String userId, String start, String end);
    long countActiveUsers(String start, String end);
    List<String> findRoomsByUser(String userId);

    // Analytics queries
    List<Map<String, Object>> getThroughputStats();
    List<Map<String, Object>> getTopUsers(int n);
    List<Map<String, Object>> getTopRooms(int n);
    List<Map<String, Object>> getUserPatterns();
}
