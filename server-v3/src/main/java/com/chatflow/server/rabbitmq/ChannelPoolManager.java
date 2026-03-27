package com.chatflow.server.rabbitmq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ShutdownListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ChannelPoolManager {

    private static final Logger log = LoggerFactory.getLogger(ChannelPoolManager.class);

    private final BlockingQueue<Channel> pool;
    private Connection connection;
    private final String host;
    private final String username;
    private final String password;
    private final int poolSize;

    public ChannelPoolManager(String host, String username, String password, int poolSize) {
        this.host = host;
        this.username = username;
        this.password = password;
        this.poolSize = poolSize;
        this.pool = new ArrayBlockingQueue<>(poolSize);
    }

    public void init() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setUsername(username);
        factory.setPassword(password);
        factory.setAutomaticRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(5000);
        this.connection = factory.newConnection();

        connection.addShutdownListener(cause ->
                log.error("RabbitMQ connection lost: {}", cause.getMessage()));

        for (int i = 0; i < poolSize; i++) {
            Channel channel = connection.createChannel();
            channel.confirmSelect(); // enable publisher confirms
            pool.offer(channel);
        }
        log.info("Channel pool initialized: host={}, size={}", host, poolSize);
    }

    public Channel borrowChannel() throws InterruptedException {
        Channel channel = pool.poll(5, TimeUnit.SECONDS);
        if (channel == null) {
            throw new RuntimeException("Channel pool exhausted");
        }
        if (!channel.isOpen()) {
            log.warn("Borrowed channel is closed, creating replacement");
            try {
                channel = connection.createChannel();
                channel.confirmSelect();
            } catch (IOException e) {
                throw new RuntimeException("Failed to create replacement channel", e);
            }
        }
        return channel;
    }

    public void returnChannel(Channel channel) {
        if (channel != null && channel.isOpen()) {
            if (!pool.offer(channel)) {
                try { channel.close(); } catch (Exception ignored) {}
            }
        } else {
            // Replace closed channel
            try {
                Channel newChannel = connection.createChannel();
                newChannel.confirmSelect();
                pool.offer(newChannel);
            } catch (IOException e) {
                log.error("Failed to replace closed channel", e);
            }
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public void shutdown() {
        for (Channel channel : pool) {
            try { channel.close(); } catch (Exception ignored) {}
        }
        try {
            if (connection != null && connection.isOpen()) {
                connection.close();
            }
        } catch (IOException e) {
            log.error("Error closing RabbitMQ connection", e);
        }
    }
}
