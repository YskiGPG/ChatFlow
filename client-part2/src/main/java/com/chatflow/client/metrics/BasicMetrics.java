package com.chatflow.client.metrics;

import java.util.concurrent.atomic.AtomicLong;

public class BasicMetrics {

    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);
    private final AtomicLong totalConnections = new AtomicLong(0);
    private final AtomicLong reconnections = new AtomicLong(0);

    public void incrementSuccess() { successCount.incrementAndGet(); }
    public void incrementFailed() { failedCount.incrementAndGet(); }
    public void incrementConnections() { totalConnections.incrementAndGet(); }
    public void incrementReconnections() { reconnections.incrementAndGet(); }

    public long getSuccessCount() { return successCount.get(); }
    public long getFailedCount() { return failedCount.get(); }
    public long getTotalConnections() { return totalConnections.get(); }
    public long getReconnections() { return reconnections.get(); }

    public void printReport(long startTime, long endTime) {
        long elapsed = endTime - startTime;
        double seconds = elapsed / 1000.0;
        double throughput = successCount.get() / seconds;

        System.out.println("========================================");
        System.out.println("         PERFORMANCE REPORT             ");
        System.out.println("========================================");
        System.out.printf("Successful messages: %d%n", successCount.get());
        System.out.printf("Failed messages:     %d%n", failedCount.get());
        System.out.printf("Total runtime:       %.2f seconds%n", seconds);
        System.out.printf("Throughput:          %.2f messages/sec%n", throughput);
        System.out.printf("Total connections:   %d%n", totalConnections.get());
        System.out.printf("Reconnections:       %d%n", reconnections.get());
        System.out.println("========================================");
    }
}
