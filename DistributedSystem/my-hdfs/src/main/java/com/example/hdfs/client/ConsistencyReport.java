package com.example.hdfs.client;

import java.util.List;

public record ConsistencyReport(
        String path,
        boolean success,
        ConsistencySeverity severity,
        String summary,
        List<String> details
) {
    public static ConsistencyReport failed(String path, String message) {
        return new ConsistencyReport(path, false, ConsistencySeverity.CRITICAL, message, List.of(message));
    }
}
