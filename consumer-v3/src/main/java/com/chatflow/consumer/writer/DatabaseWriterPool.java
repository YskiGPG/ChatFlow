package com.chatflow.consumer.writer;

import com.chatflow.consumer.db.MessageBatchWriter;
import com.chatflow.consumer.model.QueueMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fixed thread pool that writes message batches to MySQL.
 *
 * On success  → acks each message's RabbitMQ delivery.
 * On failure  → retries with exponential backoff (max 3 attempts).
 * After max retries → nacks with requeue=false (dead-letter queue handles it).
 */
public class DatabaseWriterPool {

    private static final Logger log = LoggerFactory.getLogger(DatabaseWriterPool.class);

    private static final int MAX_RETRIES = 3;
    private static final long BASE_BACKOFF_MS = 200;

    private final ExecutorService pool;
    private final MessageBatchWriter batchWriter;

    // Stats counters (read by ConsumerMetrics)
    private final AtomicLong batchesExecuted = new AtomicLong(0);
    private final AtomicLong messagesWritten  = new AtomicLong(0);
    private final AtomicLong messagesFailed   = new AtomicLong(0);

    public DatabaseWriterPool(int writerThreads, MessageBatchWriter batchWriter) {
        this.batchWriter = batchWriter;
        this.pool = Executors.newFixedThreadPool(writerThreads, r -> {
            Thread t = new Thread(r, "db-writer-" + writerThreads);
            t.setDaemon(false);
            return t;
        });
    }

    /**
     * Submit a batch for async DB write. Returns a Future for optional tracking.
     */
    public Future<?> submit(List<QueueMessage> batch) {
        if (batch == null || batch.isEmpty()) return CompletableFuture.completedFuture(null);
        return pool.submit(() -> writeBatchWithRetry(batch));
    }

    /**
     * Write synchronously — used during graceful shutdown to drain remaining buffer.
     */
    public void writeSync(List<QueueMessage> batch) {
        if (batch == null || batch.isEmpty()) return;
        writeBatchWithRetry(batch);
    }

    public void shutdown() {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // --- Stats ---

    public long getBatchesExecuted() { return batchesExecuted.get(); }
    public long getMessagesWritten()  { return messagesWritten.get(); }
    public long getMessagesFailed()   { return messagesFailed.get(); }

    // --- Internal ---

    private void writeBatchWithRetry(List<QueueMessage> batch) {
        int attempt = 0;
        while (attempt < MAX_RETRIES) {
            try {
                int written = batchWriter.writeBatch(batch);
                messagesWritten.addAndGet(written);
                batchesExecuted.incrementAndGet();
                ackAll(batch);
                return;
            } catch (SQLException e) {
                attempt++;
                long backoff = BASE_BACKOFF_MS * (1L << (attempt - 1)); // 200, 400, 800ms
                log.warn("DB write failed (attempt {}/{}), retrying in {}ms: {}",
                    attempt, MAX_RETRIES, backoff, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try { Thread.sleep(backoff); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        // All retries exhausted — nack so dead-letter queue receives them
        log.error("Batch write failed after {} retries, sending {} messages to dead-letter",
            MAX_RETRIES, batch.size());
        messagesFailed.addAndGet(batch.size());
        nackAll(batch);
    }

    private void ackAll(List<QueueMessage> batch) {
        for (QueueMessage msg : batch) {
            if (msg.getChannel() != null) {
                try {
                    msg.getChannel().basicAck(msg.getDeliveryTag(), false);
                } catch (Exception e) {
                    log.warn("Failed to ack delivery {}: {}", msg.getDeliveryTag(), e.getMessage());
                }
            }
        }
    }

    private void nackAll(List<QueueMessage> batch) {
        for (QueueMessage msg : batch) {
            if (msg.getChannel() != null) {
                try {
                    // requeue=false → routes to dead-letter exchange if configured
                    msg.getChannel().basicNack(msg.getDeliveryTag(), false, false);
                } catch (Exception e) {
                    log.warn("Failed to nack delivery {}: {}", msg.getDeliveryTag(), e.getMessage());
                }
            }
        }
    }
}
