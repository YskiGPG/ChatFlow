package com.chatflow.server.validation;

import com.chatflow.server.model.ChatMessage;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Set;

@Component
public class MessageValidator {

    private static final Set<String> VALID_MESSAGE_TYPES = Set.of("TEXT", "JOIN", "LEAVE");

    public ValidationResult validate(ChatMessage msg) {
        if (msg == null) {
            return ValidationResult.invalid("Message cannot be null");
        }

        // userId: must be between 1 and 100000
        if (msg.getUserId() == null || msg.getUserId().isBlank()) {
            return ValidationResult.invalid("userId is required");
        }
        try {
            int userId = Integer.parseInt(msg.getUserId());
            if (userId < 1 || userId > 100000) {
                return ValidationResult.invalid("userId must be between 1 and 100000");
            }
        } catch (NumberFormatException e) {
            return ValidationResult.invalid("userId must be a numeric string");
        }

        // username: 3-20 alphanumeric characters
        if (msg.getUsername() == null || msg.getUsername().isBlank()) {
            return ValidationResult.invalid("username is required");
        }
        if (msg.getUsername().length() < 3 || msg.getUsername().length() > 20) {
            return ValidationResult.invalid("username must be 3-20 characters");
        }
        if (!msg.getUsername().matches("^[a-zA-Z0-9]+$")) {
            return ValidationResult.invalid("username must be alphanumeric");
        }

        // message: 1-500 characters
        if (msg.getMessage() == null || msg.getMessage().isEmpty()) {
            return ValidationResult.invalid("message is required");
        }
        if (msg.getMessage().length() > 500) {
            return ValidationResult.invalid("message must be 1-500 characters");
        }

        // timestamp: valid ISO-8601
        if (msg.getTimestamp() == null || msg.getTimestamp().isBlank()) {
            return ValidationResult.invalid("timestamp is required");
        }
        try {
            Instant.parse(msg.getTimestamp());
        } catch (DateTimeParseException e) {
            return ValidationResult.invalid("timestamp must be valid ISO-8601");
        }

        // messageType: TEXT, JOIN, or LEAVE
        if (msg.getMessageType() == null || !VALID_MESSAGE_TYPES.contains(msg.getMessageType())) {
            return ValidationResult.invalid("messageType must be TEXT, JOIN, or LEAVE");
        }

        return ValidationResult.valid();
    }

    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }

        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
    }
}
