package com.chatflow.server.rabbitmq;

import com.chatflow.server.model.QueueMessage;
import com.google.gson.Gson;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.MessageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class MessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(MessagePublisher.class);
    private static final String EXCHANGE_NAME = "chat.exchange";
    private final Gson gson = new Gson();
    private final ChannelPoolManager channelPool;

    public MessagePublisher(ChannelPoolManager channelPool) {
        this.channelPool = channelPool;
    }

    /**
     * Publish a message to the chat exchange with routing key room.{roomId}
     */
    public void publish(QueueMessage message) throws IOException, InterruptedException {
        String routingKey = "room." + message.getRoomId();
        byte[] body = gson.toJson(message).getBytes();

        Channel channel = channelPool.borrowChannel();
        try {
            channel.basicPublish(
                    EXCHANGE_NAME,
                    routingKey,
                    MessageProperties.PERSISTENT_TEXT_PLAIN,
                    body
            );
            channel.waitForConfirmsOrDie(5000); // wait for broker ack
        } catch (Exception e) {
            log.error("Failed to publish message: {}", e.getMessage());
            throw new IOException("Publish failed", e);
        } finally {
            channelPool.returnChannel(channel);
        }
    }
}
