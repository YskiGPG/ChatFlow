package com.chatflow.server.validation;

import com.chatflow.server.model.ChatMessage;
import com.chatflow.server.validation.MessageValidator.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MessageValidatorTest {

    private MessageValidator validator;

    @BeforeEach
    void setUp() {
        validator = new MessageValidator();
    }

    private ChatMessage validMessage() {
        return new ChatMessage("1", "user001", "Hello world", "2024-01-01T00:00:00Z", "TEXT");
    }

    // --- Valid messages ---

    @Test
    void validMessage_shouldPass() {
        ValidationResult result = validator.validate(validMessage());
        assertTrue(result.isValid());
    }

    @Test
    void validJoinMessage_shouldPass() {
        ChatMessage msg = new ChatMessage("50000", "testuser", "joining", "2024-06-15T12:30:00Z", "JOIN");
        assertTrue(validator.validate(msg).isValid());
    }

    @Test
    void validLeaveMessage_shouldPass() {
        ChatMessage msg = new ChatMessage("100000", "abc", "bye", "2024-12-31T23:59:59Z", "LEAVE");
        assertTrue(validator.validate(msg).isValid());
    }

    // --- userId validation ---

    @Test
    void nullUserId_shouldFail() {
        ChatMessage msg = validMessage();
        msg.setUserId(null);
        assertFalse(validator.validate(msg).isValid());
    }

    @Test
    void userId0_shouldFail() {
        ChatMessage msg = validMessage();
        msg.setUserId("0");
        ValidationResult result = validator.validate(msg);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("userId"));
    }

    @Test
    void userId100001_shouldFail() {
        ChatMessage msg = validMessage();
        msg.setUserId("100001");
        assertFalse(validator.validate(msg).isValid());
    }

    @Test
    void userIdNonNumeric_shouldFail() {
        ChatMessage msg = validMessage();
        msg.setUserId("abc");
        assertFalse(validator.validate(msg).isValid());
    }

    @Test
    void userIdBoundary1_shouldPass() {
        ChatMessage msg = validMessage();
        msg.setUserId("1");
        assertTrue(validator.validate(msg).isValid());
    }

    @Test
    void userIdBoundary100000_shouldPass() {
        ChatMessage msg = validMessage();
        msg.setUserId("100000");
        assertTrue(validator.validate(msg).isValid());
    }

    // --- username validation ---

    @Test
    void usernameTooShort_shouldFail() {
        ChatMessage msg = validMessage();
        msg.setUsername("ab");
        assertFalse(validator.validate(msg).isValid());
    }

    @Test
    void usernameTooLong_shouldFail() {
        ChatMessage msg = validMessage();
        msg.setUsername("a".repeat(21));
        assertFalse(validator.validate(msg).isValid());
    }

    @Test
    void usernameWithSpecialChars_shouldFail() {
        ChatMessage msg = validMessage();
        msg.setUsername("user@name");
        assertFalse(validator.validate(msg).isValid());
    }

    @Test
    void usernameExactly3Chars_shouldPass() {
        ChatMessage msg = validMessage();
        msg.setUsername("abc");
        assertTrue(validator.validate(msg).isValid());
    }

    @Test
    void usernameExactly20Chars_shouldPass() {
        ChatMessage msg = validMessage();
        msg.setUsername("a".repeat(20));
        assertTrue(validator.validate(msg).isValid());
    }

    // --- message validation ---

    @Test
    void emptyMessage_shouldFail() {
        ChatMessage msg = validMessage();
        msg.setMessage("");
        assertFalse(validator.validate(msg).isValid());
    }

    @Test
    void messageTooLong_shouldFail() {
        ChatMessage msg = validMessage();
        msg.setMessage("x".repeat(501));
        assertFalse(validator.validate(msg).isValid());
    }

    @Test
    void messageExactly500Chars_shouldPass() {
        ChatMessage msg = validMessage();
        msg.setMessage("x".repeat(500));
        assertTrue(validator.validate(msg).isValid());
    }

    // --- timestamp validation ---

    @Test
    void invalidTimestamp_shouldFail() {
        ChatMessage msg = validMessage();
        msg.setTimestamp("not-a-timestamp");
        assertFalse(validator.validate(msg).isValid());
    }

    @Test
    void nullTimestamp_shouldFail() {
        ChatMessage msg = validMessage();
        msg.setTimestamp(null);
        assertFalse(validator.validate(msg).isValid());
    }

    // --- messageType validation ---

    @Test
    void invalidMessageType_shouldFail() {
        ChatMessage msg = validMessage();
        msg.setMessageType("INVALID");
        assertFalse(validator.validate(msg).isValid());
    }

    @Test
    void nullMessageType_shouldFail() {
        ChatMessage msg = validMessage();
        msg.setMessageType(null);
        assertFalse(validator.validate(msg).isValid());
    }

    @Test
    void nullMessage_shouldFail() {
        assertFalse(validator.validate(null).isValid());
    }
}
