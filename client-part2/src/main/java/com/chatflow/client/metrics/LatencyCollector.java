package com.chatflow.client.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class LatencyCollector {

    private final ConcurrentLinkedQueue<LatencyRecord> records = new ConcurrentLinkedQueue<>();

    public void record(LatencyRecord record) {
        records.add(record);
    }

    /**
     * Returns a snapshot of all records as a List (for sorting/analysis).
     */
    public List<LatencyRecord> getRecords() {
        return new ArrayList<>(records);
    }

    public int size() {
        return records.size();
    }
}
