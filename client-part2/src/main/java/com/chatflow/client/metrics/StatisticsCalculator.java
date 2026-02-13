package com.chatflow.client.metrics;

import java.util.*;
import java.util.stream.Collectors;

public class StatisticsCalculator {

    private final List<LatencyRecord> records;

    public StatisticsCalculator(List<LatencyRecord> records) {
        this.records = records;
    }

    public void printFullReport() {
        if (records.isEmpty()) {
            System.out.println("No records to analyze.");
            return;
        }

        long[] latencies = records.stream().mapToLong(LatencyRecord::getLatencyMs).sorted().toArray();

        System.out.println("==========================================");
        System.out.println("       DETAILED PERFORMANCE REPORT        ");
        System.out.println("==========================================");
        System.out.printf("Total messages:       %d%n", latencies.length);
        System.out.printf("Mean response time:   %.2f ms%n", mean(latencies));
        System.out.printf("Median response time: %d ms%n", percentile(latencies, 50));
        System.out.printf("95th percentile:      %d ms%n", percentile(latencies, 95));
        System.out.printf("99th percentile:      %d ms%n", percentile(latencies, 99));
        System.out.printf("Min response time:    %d ms%n", latencies[0]);
        System.out.printf("Max response time:    %d ms%n", latencies[latencies.length - 1]);
        System.out.println();

        printMessageTypeDistribution();
        System.out.println();
        printThroughputPerRoom();
        System.out.println("==========================================");
    }

    private double mean(long[] sorted) {
        long sum = 0;
        for (long v : sorted) sum += v;
        return (double) sum / sorted.length;
    }

    private long percentile(long[] sorted, int p) {
        int index = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
    }

    private void printMessageTypeDistribution() {
        Map<String, Long> typeCounts = records.stream()
                .collect(Collectors.groupingBy(LatencyRecord::getMessageType, Collectors.counting()));

        System.out.println("Message Type Distribution:");
        long total = records.size();
        typeCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> System.out.printf("  %-8s %6d  (%.1f%%)%n",
                        e.getKey(), e.getValue(), 100.0 * e.getValue() / total));
    }

    private void printThroughputPerRoom() {
        Map<Integer, List<LatencyRecord>> byRoom = records.stream()
                .collect(Collectors.groupingBy(LatencyRecord::getRoomId));

        System.out.println("Throughput per Room:");
        byRoom.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    List<LatencyRecord> roomRecords = e.getValue();
                    long minTs = roomRecords.stream().mapToLong(LatencyRecord::getSendTimestamp).min().orElse(0);
                    long maxTs = roomRecords.stream().mapToLong(LatencyRecord::getSendTimestamp).max().orElse(0);
                    double durationSec = Math.max((maxTs - minTs) / 1000.0, 0.001);
                    System.out.printf("  Room %-3d %6d msgs  %.0f msg/sec%n",
                            e.getKey(), roomRecords.size(), roomRecords.size() / durationSec);
                });
    }
}
