package com.chatflow.server.config;

import com.chatflow.server.rabbitmq.ChannelPoolManager;
import com.rabbitmq.client.Channel;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

@Configuration
public class RabbitMQConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQConfig.class);
    public static final String EXCHANGE_NAME = "chat.exchange";
    public static final String BINDING_KEY = "room.*";

    @Value("${rabbitmq.host:localhost}")
    private String rabbitHost;

    @Value("${rabbitmq.username:guest}")
    private String rabbitUsername;

    @Value("${rabbitmq.password:guest}")
    private String rabbitPassword;

    @Value("${rabbitmq.channel.pool.size:20}")
    private int channelPoolSize;

    @Value("${server.id:#{T(java.util.UUID).randomUUID().toString().substring(0,8)}}")
    private String serverId;

    @Bean
    public String serverId() {
        return serverId;
    }

    @Bean
    public ChannelPoolManager channelPoolManager() throws IOException, TimeoutException {
        ChannelPoolManager pool = new ChannelPoolManager(rabbitHost, rabbitUsername, rabbitPassword, channelPoolSize);
        pool.init();
        return pool;
    }

    @Bean
    public String exclusiveQueueName(ChannelPoolManager channelPoolManager) throws Exception {
        // Declare topology using the already-created pool
        Channel channel = channelPoolManager.borrowChannel();
        String queueName;
        try {
            // Declare topic exchange (idempotent)
            channel.exchangeDeclare(EXCHANGE_NAME, "topic", true);

            // Declare exclusive queue for this server instance
            queueName = "server." + serverId + ".queue";
            channel.queueDeclare(queueName, false, false, true, null);

            // Bind to all room messages
            channel.queueBind(queueName, EXCHANGE_NAME, BINDING_KEY);

            log.info("RabbitMQ topology ready: exchange={}, queue={}, binding={}",
                    EXCHANGE_NAME, queueName, BINDING_KEY);
        } finally {
            channelPoolManager.returnChannel(channel);
        }
        return queueName;
    }

    @PreDestroy
    public void cleanup() {
        // ChannelPoolManager handles shutdown via Spring lifecycle
    }
}
