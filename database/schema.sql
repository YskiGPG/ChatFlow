-- ChatFlow A3 Database Schema
-- MySQL 8.0

CREATE DATABASE IF NOT EXISTS chatflow CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE chatflow;

CREATE TABLE IF NOT EXISTS messages (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id      VARCHAR(36)  NOT NULL UNIQUE,
    room_id         VARCHAR(20)  NOT NULL,
    user_id         VARCHAR(20)  NOT NULL,
    username        VARCHAR(20)  NOT NULL,
    message         VARCHAR(500) NOT NULL,
    message_type    ENUM('TEXT', 'JOIN', 'LEAVE') NOT NULL,
    timestamp       DATETIME(3)  NOT NULL,
    server_id       VARCHAR(50),
    client_ip       VARCHAR(45),
    created_at      DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),

    INDEX idx_room_time (room_id, timestamp),
    INDEX idx_user_time (user_id, timestamp),
    INDEX idx_timestamp (timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
