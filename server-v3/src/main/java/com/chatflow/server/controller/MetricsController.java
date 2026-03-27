package com.chatflow.server.controller;

import com.chatflow.server.model.MessageRecord;
import com.chatflow.server.repository.MessageRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final MessageRepository repo;

    public MetricsController(MessageRepository repo) {
        this.repo = repo;
    }

    // -----------------------------------------------------------------------
    // Core query endpoints (Phase 7)
    // -----------------------------------------------------------------------

    /**
     * GET /api/metrics/rooms/{roomId}/messages?start=&end=
     * Returns all messages in a room within a time range.
     * start/end: ISO-8601 datetime strings, e.g. 2024-01-15T00:00:00
     */
    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<List<MessageRecord>> getRoomMessages(
            @PathVariable("roomId") String roomId,
            @RequestParam("start") String start,
            @RequestParam("end") String end) {
        List<MessageRecord> messages = repo.findByRoomAndTimeRange(roomId, start, end);
        return ResponseEntity.ok(messages);
    }

    /**
     * GET /api/metrics/users/{userId}/messages?start=&end=
     * Returns all messages sent by a user within a time range.
     */
    @GetMapping("/users/{userId}/messages")
    public ResponseEntity<List<MessageRecord>> getUserMessages(
            @PathVariable("userId") String userId,
            @RequestParam("start") String start,
            @RequestParam("end") String end) {
        List<MessageRecord> messages = repo.findByUserAndTimeRange(userId, start, end);
        return ResponseEntity.ok(messages);
    }

    /**
     * GET /api/metrics/active-users?start=&end=
     * Returns count of distinct users who sent messages in the time range.
     */
    @GetMapping("/active-users")
    public ResponseEntity<Map<String, Object>> getActiveUsers(
            @RequestParam("start") String start,
            @RequestParam("end") String end) {
        long count = repo.countActiveUsers(start, end);
        return ResponseEntity.ok(Map.of(
            "start", start,
            "end",   end,
            "activeUsers", count
        ));
    }

    /**
     * GET /api/metrics/users/{userId}/rooms
     * Returns list of rooms the user has participated in.
     */
    @GetMapping("/users/{userId}/rooms")
    public ResponseEntity<Map<String, Object>> getUserRooms(
            @PathVariable("userId") String userId) {
        List<String> rooms = repo.findRoomsByUser(userId);
        return ResponseEntity.ok(Map.of(
            "userId", userId,
            "rooms",  rooms,
            "count",  rooms.size()
        ));
    }

    // -----------------------------------------------------------------------
    // Analytics endpoints (Phase 8)
    // -----------------------------------------------------------------------

    /**
     * GET /api/metrics/analytics/throughput
     * Returns message count per minute (last 60 minutes).
     */
    @GetMapping("/analytics/throughput")
    public ResponseEntity<List<Map<String, Object>>> getThroughput() {
        return ResponseEntity.ok(repo.getThroughputStats());
    }

    /**
     * GET /api/metrics/analytics/top-users?n=10
     * Returns top N users by message count.
     */
    @GetMapping("/analytics/top-users")
    public ResponseEntity<List<Map<String, Object>>> getTopUsers(
            @RequestParam(name = "n", defaultValue = "10") int n) {
        return ResponseEntity.ok(repo.getTopUsers(n));
    }

    /**
     * GET /api/metrics/analytics/top-rooms?n=10
     * Returns top N rooms by message count.
     */
    @GetMapping("/analytics/top-rooms")
    public ResponseEntity<List<Map<String, Object>>> getTopRooms(
            @RequestParam(name = "n", defaultValue = "10") int n) {
        return ResponseEntity.ok(repo.getTopRooms(n));
    }

    /**
     * GET /api/metrics/analytics/user-patterns
     * Returns per-user activity summary (rooms visited, total messages, first/last seen).
     */
    @GetMapping("/analytics/user-patterns")
    public ResponseEntity<List<Map<String, Object>>> getUserPatterns() {
        return ResponseEntity.ok(repo.getUserPatterns());
    }
}
