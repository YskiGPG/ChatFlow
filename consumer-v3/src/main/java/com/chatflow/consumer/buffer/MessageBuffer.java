package com.chatflow.consumer.buffer;

import com.chatflow.consumer.model.QueueMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Thread-safe in-memory buffer for RabbitMQ messages.
 *
 * Flushes a batch when:
 *   1. Buffer size reaches batchSize (size-triggered)
 *   2. flushIntervalMs elapses since last flush (time-triggered)
 *
 * The flush callback receives a drained List<QueueMessage> for DB writing.
 */
public class MessageBuffer {

    private static final Logger log = LoggerFactory.getLogger(MessageBuffer.class);

    private final int batchSize;
    private final long flushIntervalMs;
    private final Consumer<List<QueueMessage>> flushCallback;

    private final ConcurrentLinkedQueue<QueueMessage> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger size = new AtomicInteger(0);

    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "buffer-flush-timer");
            t.setDaemon(true);
            return t;
        });

    public MessageBuffer(int batchSize, long flushIntervalMs, Consumer<List<QueueMessage>> flushCallback) {
        this.batchSize = batchSize;
        this.flushIntervalMs = flushIntervalMs;
        this.flushCallback = flushCallback;

        scheduler.scheduleAtFixedRate(
            this::timedFlush,
            flushIntervalMs,
            flushIntervalMs,
            TimeUnit.MILLISECONDS
        );
    }

    /**
     * Add a message to the buffer. Triggers a size-based flush if threshold is reached.
     */
    public void add(QueueMessage message) {
        queue.offer(message);
        if (size.incrementAndGet() >= batchSize) {
            sizeTriggeredFlush();
        }
    }

    /**
     * Drain up to batchSize messages atomically and return them.
     */
    public List<QueueMessage> drainBatch(int maxSize) {
        List<QueueMessage> batch = new ArrayList<>(maxSize);
        QueueMessage msg;
        int drained = 0;
        while (drained < maxSize && (msg = queue.poll()) != null) {
            batch.add(msg);
            size.decrementAndGet();
            drained++;
        }
        return batch;
    }

    /**
     * Drain everything remaining — used during graceful shutdown.
     */
    public List<QueueMessage> drainAll() {
        List<QueueMessage> all = new ArrayList<>();
        QueueMessage msg;
        while ((msg = queue.poll()) != null) {
            all.add(msg);
            size.decrementAndGet();
        }
        return all;
    }

    public int currentSize() {
        return size.get();
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // --- Internal flush logic ---

    private void sizeTriggeredFlush() {
        List<QueueMessage> batch = drainBatch(batchSize);
        if (!batch.isEmpty()) {
            log.debug("Size-triggered flush: {} messages", batch.size());
            flushCallback.accept(batch);
        }
    }

    private void timedFlush() {
        if (size.get() == 0) return;
        List<QueueMessage> batch = drainAll();
        if (!batch.isEmpty()) {
            log.debug("Time-triggered flush: {} messages", batch.size());
            flushCallback.accept(batch);
        }
    }
}
