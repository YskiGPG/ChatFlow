package com.chatflow.consumer.buffer;

import com.chatflow.consumer.model.QueueMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MessageBufferTest {

    // --- drainBatch ---

    @Test
    void drainBatch_returnsUpToBatchSize() {
        List<List<QueueMessage>> flushed = new ArrayList<>();
        MessageBuffer buffer = new MessageBuffer(100, 60_000, flushed::add);

        for (int i = 0; i < 10; i++) buffer.add(msg("m" + i));

        List<QueueMessage> drained = buffer.drainBatch(5);
        assertEquals(5, drained.size());
        assertEquals(5, buffer.currentSize());

        buffer.shutdown();
    }

    @Test
    void drainBatch_emptyBuffer_returnsEmptyList() {
        MessageBuffer buffer = new MessageBuffer(100, 60_000, ignored -> {});
        List<QueueMessage> drained = buffer.drainBatch(10);
        assertTrue(drained.isEmpty());
        buffer.shutdown();
    }

    @Test
    void drainAll_drainsEverything() {
        List<List<QueueMessage>> flushed = new ArrayList<>();
        MessageBuffer buffer = new MessageBuffer(100, 60_000, flushed::add);

        for (int i = 0; i < 7; i++) buffer.add(msg("x" + i));

        List<QueueMessage> all = buffer.drainAll();
        assertEquals(7, all.size());
        assertEquals(0, buffer.currentSize());

        buffer.shutdown();
    }

    // --- Size-triggered flush ---

    @Test
    void sizeTriggeredFlush_firesWhenBatchSizeReached() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger flushCount = new AtomicInteger(0);

        MessageBuffer buffer = new MessageBuffer(5, 60_000, batch -> {
            flushCount.addAndGet(batch.size());
            latch.countDown();
        });

        for (int i = 0; i < 5; i++) buffer.add(msg("s" + i));

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Flush not triggered within 2s");
        assertEquals(5, flushCount.get());

        buffer.shutdown();
    }

    @Test
    void sizeTriggeredFlush_doesNotFireBeforeBatchSize() throws InterruptedException {
        AtomicInteger callCount = new AtomicInteger(0);
        MessageBuffer buffer = new MessageBuffer(10, 60_000, batch -> callCount.incrementAndGet());

        for (int i = 0; i < 9; i++) buffer.add(msg("t" + i));
        Thread.sleep(100); // give any spurious flush a chance to fire

        assertEquals(0, callCount.get());
        buffer.shutdown();
    }

    // --- Time-triggered flush ---

    @Test
    void timedFlush_firesAfterInterval() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger flushSize = new AtomicInteger(0);

        MessageBuffer buffer = new MessageBuffer(1000, 200, batch -> {
            flushSize.addAndGet(batch.size());
            latch.countDown();
        });

        buffer.add(msg("timer-1"));
        buffer.add(msg("timer-2"));

        assertTrue(latch.await(1, TimeUnit.SECONDS), "Timed flush not triggered within 1s");
        assertEquals(2, flushSize.get());

        buffer.shutdown();
    }

    // --- Thread safety ---

    @Test
    void threadSafe_concurrentAdds_noLostMessages() throws Exception {
        int threads = 8;
        int perThread = 100;
        ConcurrentLinkedQueue<QueueMessage> collected = new ConcurrentLinkedQueue<>();

        MessageBuffer buffer = new MessageBuffer(10_000, 60_000, collected::addAll);

        ExecutorService exec = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final int tid = t;
            exec.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        buffer.add(msg("t" + tid + "-m" + i));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        done.await(5, TimeUnit.SECONDS);
        exec.shutdown();

        // Drain whatever remains in buffer
        collected.addAll(buffer.drainAll());
        buffer.shutdown();

        assertEquals(threads * perThread, collected.size());
    }

    // --- Helper ---

    private QueueMessage msg(String id) {
        QueueMessage m = new QueueMessage();
        m.setMessageId(id);
        m.setRoomId("room1");
        m.setUserId("user1");
        m.setUsername("u");
        m.setMessage("hello");
        m.setMessageType("TEXT");
        m.setTimestamp("2024-01-15T10:00:00.000Z");
        return m;
    }
}
