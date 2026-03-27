package com.chatflow.consumer.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks pipeline counters and logs a summary every 10 seconds.
 */
public class ConsumerMetrics {

    private static final Logger log = LoggerFactory.getLogger(ConsumerMetrics.class);

    private final AtomicLong messagesConsumed = new AtomicLong(0);
    private final AtomicLong messagesWritten  = new AtomicLong(0);
    private final AtomicLong messagesFailed   = new AtomicLong(0);
    private final AtomicLong batchesExecuted  = new AtomicLong(0);

    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "metrics-reporter");
            t.setDaemon(true);
            return t;
        });

    private final long startTimeMs = System.currentTimeMillis();

    public ConsumerMetrics() {
        scheduler.scheduleAtFixedRate(this::report, 10, 10, TimeUnit.SECONDS);
    }

    public void incrementConsumed()           { messagesConsumed.incrementAndGet(); }
    public void incrementConsumed(long n)     { messagesConsumed.addAndGet(n); }
    public void addWritten(long n)            { messagesWritten.addAndGet(n); }
    public void addFailed(long n)             { messagesFailed.addAndGet(n); }
    public void incrementBatches()            { batchesExecuted.incrementAndGet(); }

    public long getMessagesConsumed()  { return messagesConsumed.get(); }
    public long getMessagesWritten()   { return messagesWritten.get(); }
    public long getMessagesFailed()    { return messagesFailed.get(); }
    public long getBatchesExecuted()   { return batchesExecuted.get(); }

    public void report() {
        long uptimeSec = (System.currentTimeMillis() - startTimeMs) / 1000;
        long consumed  = messagesConsumed.get();
        long written   = messagesWritten.get();
        long failed    = messagesFailed.get();
        long batches   = batchesExecuted.get();
        double tps     = uptimeSec > 0 ? (double) consumed / uptimeSec : 0;

        log.info("[Metrics] uptime={}s consumed={} written={} failed={} batches={} tps={}",
            uptimeSec, consumed, written, failed, batches, String.format("%.1f", tps));
    }

    public void shutdown() {
        report(); // final snapshot
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
