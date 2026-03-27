package com.chatflow.server.controller;

import com.chatflow.server.model.MessageRecord;
import com.chatflow.server.repository.MessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MetricsController.class)
class MetricsControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    MessageRepository repo;

    // -----------------------------------------------------------------------
    // Core endpoints
    // -----------------------------------------------------------------------

    @Test
    void getRoomMessages_returns200WithList() throws Exception {
        when(repo.findByRoomAndTimeRange(eq("room1"), any(), any()))
            .thenReturn(List.of(record("msg-1", "room1", "user1")));

        mvc.perform(get("/api/metrics/rooms/room1/messages")
                .param("start", "2024-01-15T00:00:00")
                .param("end",   "2024-01-15T23:59:59"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].messageId").value("msg-1"))
            .andExpect(jsonPath("$[0].roomId").value("room1"));
    }

    @Test
    void getRoomMessages_emptyRange_returnsEmptyList() throws Exception {
        when(repo.findByRoomAndTimeRange(any(), any(), any())).thenReturn(List.of());

        mvc.perform(get("/api/metrics/rooms/room99/messages")
                .param("start", "2024-01-01T00:00:00")
                .param("end",   "2024-01-01T01:00:00"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getUserMessages_returns200WithList() throws Exception {
        when(repo.findByUserAndTimeRange(eq("user1"), any(), any()))
            .thenReturn(List.of(
                record("msg-1", "room1", "user1"),
                record("msg-2", "room2", "user1")
            ));

        mvc.perform(get("/api/metrics/users/user1/messages")
                .param("start", "2024-01-15T00:00:00")
                .param("end",   "2024-01-15T23:59:59"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getActiveUsers_returnsCount() throws Exception {
        when(repo.countActiveUsers(any(), any())).thenReturn(42L);

        mvc.perform(get("/api/metrics/active-users")
                .param("start", "2024-01-15T00:00:00")
                .param("end",   "2024-01-15T23:59:59"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activeUsers").value(42));
    }

    @Test
    void getActiveUsers_zeroWhenNoMessages() throws Exception {
        when(repo.countActiveUsers(any(), any())).thenReturn(0L);

        mvc.perform(get("/api/metrics/active-users")
                .param("start", "2024-01-01T00:00:00")
                .param("end",   "2024-01-01T00:00:01"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activeUsers").value(0));
    }

    @Test
    void getUserRooms_returnsRoomList() throws Exception {
        when(repo.findRoomsByUser("user1")).thenReturn(List.of("room1", "room3", "room7"));

        mvc.perform(get("/api/metrics/users/user1/rooms"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value("user1"))
            .andExpect(jsonPath("$.count").value(3))
            .andExpect(jsonPath("$.rooms.length()").value(3));
    }

    // -----------------------------------------------------------------------
    // Analytics endpoints (Phase 8 tests)
    // -----------------------------------------------------------------------

    @Test
    void getThroughput_returns200() throws Exception {
        when(repo.getThroughputStats()).thenReturn(List.of(
            Map.of("minute_bucket", "2024-01-15 10:00:00", "message_count", 150)
        ));

        mvc.perform(get("/api/metrics/analytics/throughput"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].message_count").value(150));
    }

    @Test
    void getTopUsers_defaultN10() throws Exception {
        when(repo.getTopUsers(10)).thenReturn(List.of(
            Map.of("user_id", "user1", "username", "alice", "message_count", 500)
        ));

        mvc.perform(get("/api/metrics/analytics/top-users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].user_id").value("user1"));
    }

    @Test
    void getTopUsers_customN() throws Exception {
        when(repo.getTopUsers(5)).thenReturn(List.of());

        mvc.perform(get("/api/metrics/analytics/top-users").param("n", "5"))
            .andExpect(status().isOk());
    }

    @Test
    void getTopRooms_returns200() throws Exception {
        when(repo.getTopRooms(10)).thenReturn(List.of(
            Map.of("room_id", "room1", "message_count", 1000, "unique_users", 20)
        ));

        mvc.perform(get("/api/metrics/analytics/top-rooms"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].room_id").value("room1"));
    }

    @Test
    void getUserPatterns_returns200() throws Exception {
        when(repo.getUserPatterns()).thenReturn(List.of(
            Map.of("user_id", "user1", "total_messages", 200, "rooms_visited", 5)
        ));

        mvc.perform(get("/api/metrics/analytics/user-patterns"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].user_id").value("user1"));
    }

    // --- Helper ---

    private MessageRecord record(String msgId, String roomId, String userId) {
        MessageRecord r = new MessageRecord();
        r.setMessageId(msgId);
        r.setRoomId(roomId);
        r.setUserId(userId);
        r.setUsername("testUser");
        r.setMessage("hello");
        r.setMessageType("TEXT");
        r.setTimestamp("2024-01-15T10:00:00.000");
        return r;
    }
}
