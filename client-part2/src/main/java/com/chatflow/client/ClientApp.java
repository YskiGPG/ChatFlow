package com.chatflow.client;

import com.chatflow.client.config.ClientConfig;
import com.chatflow.client.connection.ConnectionManager;
import com.chatflow.client.generator.MessageGenerator;
import com.chatflow.client.metrics.*;
import com.chatflow.client.retry.RetryHandler;
import com.chatflow.client.sender.MessageSender;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class ClientApp {

    public static void main(String[] args) throws Exception {
        ClientConfig.init(args);

        System.out.println("ChatFlow Load Test Client - Part 2 (with latency analysis)");
        System.out.println("===========================================================");
        System.out.printf("Total messages: %d%n", ClientConfig.TOTAL_MESSAGES);
        System.out.printf("Warmup: %d threads, %d messages total%n",
                ClientConfig.WARMUP_THREADS, ClientConfig.WARMUP_TOTAL);
        System.out.printf("Main phase: %d threads, %d messages%n",
                ClientConfig.MAIN_PHASE_THREADS, ClientConfig.MAIN_PHASE_MESSAGES);
        System.out.println();

        BlockingQueue<String> queue = new ArrayBlockingQueue<>(ClientConfig.QUEUE_CAPACITY);
        LatencyCollector allLatencies = new LatencyCollector();

        // =====================
        // Warmup Phase
        // =====================
        BasicMetrics warmupMetrics = new BasicMetrics();
        ConnectionManager warmupConnManager = new ConnectionManager(warmupMetrics);
        RetryHandler warmupRetryHandler = new RetryHandler(warmupConnManager);

        System.out.println("Starting warmup phase...");
        long warmupStart = System.currentTimeMillis();

        Thread generatorThread = new Thread(
                new MessageGenerator(queue, ClientConfig.WARMUP_TOTAL), "generator-warmup");
        generatorThread.start();

        int warmupPerThread = ClientConfig.WARMUP_TOTAL / ClientConfig.WARMUP_THREADS;
        int warmupRemainder = ClientConfig.WARMUP_TOTAL % ClientConfig.WARMUP_THREADS;

        List<Thread> warmupThreads = new ArrayList<>();
        for (int i = 0; i < ClientConfig.WARMUP_THREADS; i++) {
            int count = warmupPerThread + (i < warmupRemainder ? 1 : 0);
            if (count <= 0) break;
            int roomId = (i % ClientConfig.NUM_ROOMS) + 1;
            Thread t = new Thread(
                    new MessageSender(queue, count, roomId,
                            warmupConnManager, warmupRetryHandler, warmupMetrics, allLatencies),
                    "sender-warmup-" + i);
            warmupThreads.add(t);
            t.start();
        }

        generatorThread.join();
        for (Thread t : warmupThreads) {
            t.join();
        }

        long warmupEnd = System.currentTimeMillis();
        System.out.println("Warmup phase complete.");
        warmupMetrics.printReport(warmupStart, warmupEnd);

        // =====================
        // Main Phase
        // =====================
        if (ClientConfig.MAIN_PHASE_MESSAGES > 0) {
            BasicMetrics mainMetrics = new BasicMetrics();
            ConnectionManager mainConnManager = new ConnectionManager(mainMetrics);
            RetryHandler mainRetryHandler = new RetryHandler(mainConnManager);

            System.out.println("\nStarting main phase...");
            long mainStart = System.currentTimeMillis();

            Thread mainGeneratorThread = new Thread(
                    new MessageGenerator(queue, ClientConfig.MAIN_PHASE_MESSAGES), "generator-main");
            mainGeneratorThread.start();

            int mainPerThread = ClientConfig.MAIN_PHASE_MESSAGES / ClientConfig.MAIN_PHASE_THREADS;
            int mainRemainder = ClientConfig.MAIN_PHASE_MESSAGES % ClientConfig.MAIN_PHASE_THREADS;

            List<Thread> mainThreads = new ArrayList<>();
            for (int i = 0; i < ClientConfig.MAIN_PHASE_THREADS; i++) {
                int count = mainPerThread + (i < mainRemainder ? 1 : 0);
                if (count <= 0) break;
                int roomId = (i % ClientConfig.NUM_ROOMS) + 1;
                Thread t = new Thread(
                        new MessageSender(queue, count, roomId,
                                mainConnManager, mainRetryHandler, mainMetrics, allLatencies),
                        "sender-main-" + i);
                mainThreads.add(t);
                t.start();
            }

            mainGeneratorThread.join();
            for (Thread t : mainThreads) {
                t.join();
            }

            long mainEnd = System.currentTimeMillis();
            System.out.println("Main phase complete.");
            mainMetrics.printReport(mainStart, mainEnd);

            // Overall basic report
            System.out.println("\n========= OVERALL =========");
            long totalSuccess = warmupMetrics.getSuccessCount() + mainMetrics.getSuccessCount();
            long totalFailed = warmupMetrics.getFailedCount() + mainMetrics.getFailedCount();
            double totalSeconds = (mainEnd - warmupStart) / 1000.0;
            System.out.printf("Total successful: %d%n", totalSuccess);
            System.out.printf("Total failed:     %d%n", totalFailed);
            System.out.printf("Total time:       %.2f seconds%n", totalSeconds);
            System.out.printf("Overall throughput: %.2f msg/sec%n", totalSuccess / totalSeconds);
        } else {
            System.out.println("\nNo main phase needed (all messages sent in warmup).");
        }

        // =====================
        // Detailed Analysis
        // =====================
        System.out.println();
        List<LatencyRecord> records = allLatencies.getRecords();

        // Statistics
        StatisticsCalculator stats = new StatisticsCalculator(records);
        stats.printFullReport();

        // CSV export
        String csvPath = "results/latency_data.csv";
        CsvExporter.export(records, csvPath);

        // Throughput chart
        String chartPath = "results/throughput_chart.png";
        ThroughputChartGenerator.generate(records, chartPath);

        System.out.println("\nDone!");
    }
}
