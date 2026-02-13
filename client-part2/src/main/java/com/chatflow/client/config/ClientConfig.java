package com.chatflow.client.config;

public class ClientConfig {
    public static String SERVER_URI = "ws://localhost:8080/chat/";
    public static int TOTAL_MESSAGES = 500_000;
    public static final int WARMUP_THREADS = 32;
    public static final int WARMUP_MESSAGES_PER_THREAD = 1000;
    public static final int MAIN_PHASE_THREADS = 128; // tunable
    public static final int QUEUE_CAPACITY = 10_000;
    public static final int MAX_RETRIES = 5;
    public static final int NUM_ROOMS = 20;
    public static final int MESSAGE_POOL_SIZE = 50;
    public static final long ACK_TIMEOUT_MS = 5000;

    // Derived values (computed after TOTAL_MESSAGES is finalized)
    public static int WARMUP_TOTAL;
    public static int MAIN_PHASE_MESSAGES;

    public static void init(String[] args) {
        // Allow override: java -jar client.jar 1000
        if (args.length > 0) {
            TOTAL_MESSAGES = Integer.parseInt(args[0]);
        }
        if (args.length > 1) {
            SERVER_URI = args[1];
        }

        // Warmup cannot exceed total
        WARMUP_TOTAL = Math.min(TOTAL_MESSAGES, WARMUP_THREADS * WARMUP_MESSAGES_PER_THREAD);
        MAIN_PHASE_MESSAGES = TOTAL_MESSAGES - WARMUP_TOTAL;
    }
}
