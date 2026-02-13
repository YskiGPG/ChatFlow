package com.chatflow.client.metrics;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ThroughputChartGenerator {

    private static final int BUCKET_SIZE_MS = 10_000; // 10 seconds

    public static void generate(List<LatencyRecord> records, String filePath) throws IOException {
        if (records.isEmpty()) {
            System.out.println("No records for chart generation.");
            return;
        }

        long startTime = records.stream().mapToLong(LatencyRecord::getSendTimestamp).min().orElse(0);
        long endTime = records.stream().mapToLong(LatencyRecord::getSendTimestamp).max().orElse(0);

        // Group into 10-second buckets
        TreeMap<Integer, Integer> buckets = new TreeMap<>();
        for (LatencyRecord record : records) {
            int bucketIndex = (int) ((record.getSendTimestamp() - startTime) / BUCKET_SIZE_MS);
            buckets.merge(bucketIndex, 1, Integer::sum);
        }

        int lastBucketIndex = buckets.lastKey();
        double totalDurationMs = endTime - startTime;

        // Build chart data
        XYSeries series = new XYSeries("Throughput");
        for (Map.Entry<Integer, Integer> entry : buckets.entrySet()) {
            int bucketStart = entry.getKey() * (BUCKET_SIZE_MS / 1000); // seconds

            // Last bucket may be partial - use actual duration
            double bucketDurationSec;
            if (entry.getKey() == lastBucketIndex) {
                double partialMs = totalDurationMs - (long) lastBucketIndex * BUCKET_SIZE_MS;
                bucketDurationSec = Math.max(partialMs / 1000.0, 0.1); // avoid div by zero
            } else {
                bucketDurationSec = BUCKET_SIZE_MS / 1000.0;
            }

            double throughput = entry.getValue() / bucketDurationSec;
            series.add(bucketStart, throughput);
        }

        XYSeriesCollection dataset = new XYSeriesCollection(series);
        JFreeChart chart = ChartFactory.createXYLineChart(
                "Throughput Over Time",
                "Time (seconds)",
                "Messages / Second",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        ChartUtils.saveChartAsPNG(new File(filePath), chart, 1000, 500);
        System.out.println("Chart saved to: " + filePath);
    }
}
