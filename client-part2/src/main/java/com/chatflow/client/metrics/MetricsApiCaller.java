package com.chatflow.client.metrics;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Calls all Metrics API endpoints on server-v3 after the load test and logs JSON responses.
 */
public class MetricsApiCaller {

    private static final DateTimeFormatter ISO_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC);

    private final String baseUrl;   // e.g. http://localhost:8080
    private final HttpClient http;

    public MetricsApiCaller(String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.http = HttpClient.newHttpClient();
    }

    /**
     * Call all 8 metrics endpoints. Uses testStart/testEnd as the time window,
     * sampleRoomId and sampleUserId for parameterised queries, topN for top-N analytics.
     */
    public void callAll(Instant testStart, Instant testEnd, String sampleRoomId,
                        String sampleUserId, int topN) {
        String start = ISO_FMT.format(testStart);
        String end   = ISO_FMT.format(testEnd);

        System.out.println("\n========= METRICS API RESULTS =========");

        get("Room messages",
                "/api/metrics/rooms/" + sampleRoomId + "/messages"
                        + "?start=" + enc(start) + "&end=" + enc(end));

        get("User messages",
                "/api/metrics/users/" + sampleUserId + "/messages"
                        + "?start=" + enc(start) + "&end=" + enc(end));

        get("Active users",
                "/api/metrics/active-users"
                        + "?start=" + enc(start) + "&end=" + enc(end));

        get("User rooms",
                "/api/metrics/users/" + sampleUserId + "/rooms");

        get("Throughput analytics",
                "/api/metrics/analytics/throughput");

        get("Top users",
                "/api/metrics/analytics/top-users?n=" + topN);

        get("Top rooms",
                "/api/metrics/analytics/top-rooms?n=" + topN);

        get("User patterns",
                "/api/metrics/analytics/user-patterns");

        System.out.println("========= END METRICS API RESULTS =========\n");
    }

    private void get(String label, String path) {
        String url = baseUrl + path;
        System.out.printf("%n--- %s ---%n  URL: %s%n", label, url);
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            System.out.printf("  Status: %d%n  Body: %s%n", resp.statusCode(), resp.body());
        } catch (Exception e) {
            System.out.printf("  ERROR: %s%n", e.getMessage());
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
