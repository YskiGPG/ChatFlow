package com.chatflow.client.metrics;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class CsvExporter {

    public static void export(List<LatencyRecord> records, String filePath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write(LatencyRecord.csvHeader());
            writer.newLine();
            for (LatencyRecord record : records) {
                writer.write(record.toCsvLine());
                writer.newLine();
            }
        }
        System.out.println("CSV exported to: " + filePath);
    }
}
