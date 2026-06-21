package com.apigateway.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String METRICS_PREFIX = "gateway:metrics:";
    private static final String LOGS_PREFIX = "gateway:request:log:";
    private static final String QPS_KEY = METRICS_PREFIX + "qps:";
    private static final String LATENCY_KEY = METRICS_PREFIX + "latency:";
    private static final String STATUS_KEY = METRICS_PREFIX + "status:";

    public Map<String, Object> getQpsMetrics(Long tenantId, String appId, Integer minutes) {
        int windowMinutes = minutes != null ? minutes : 60;
        Map<String, Object> result = new LinkedHashMap<>();

        List<String> timeWindows = generateTimeWindows(windowMinutes);
        List<Long> qpsValues = new ArrayList<>();

        for (String window : timeWindows) {
            String key = buildKey(QPS_KEY, tenantId, appId, window);
            Long count = redisTemplate.opsForHyperLogLog().size(key);
            qpsValues.add(count != null ? count : 0L);
        }

        long totalQps = qpsValues.stream().mapToLong(Long::longValue).sum();
        double avgQps = qpsValues.isEmpty() ? 0 : (double) totalQps / qpsValues.size();
        long peakQps = qpsValues.stream().mapToLong(Long::longValue).max().orElse(0L);

        result.put("timeWindows", timeWindows);
        result.put("qpsValues", qpsValues);
        result.put("totalRequests", totalQps);
        result.put("avgQps", Math.round(avgQps * 100) / 100.0);
        result.put("peakQps", peakQps);
        result.put("windowMinutes", windowMinutes);

        return result;
    }

    public Map<String, Object> getStatusMetrics(Long tenantId, String appId, Integer minutes) {
        int windowMinutes = minutes != null ? minutes : 60;
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Long> statusDistribution = new TreeMap<>();

        List<String> timeWindows = generateTimeWindows(windowMinutes);
        long totalRequests = 0;

        for (String window : timeWindows) {
            String pattern = buildKey(STATUS_KEY, tenantId, appId, window) + "*";
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null) {
                for (String key : keys) {
                    String statusCode = extractStatusCode(key);
                    Long count = redisTemplate.opsForHyperLogLog().size(key);
                    if (count != null && count > 0) {
                        statusDistribution.merge(statusCode, count, Long::sum);
                        totalRequests += count;
                    }
                }
            }
        }

        Map<String, Double> statusPercentages = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : statusDistribution.entrySet()) {
            double percentage = totalRequests > 0 ? (entry.getValue() * 100.0 / totalRequests) : 0;
            statusPercentages.put(entry.getKey(), Math.round(percentage * 100) / 100.0);
        }

        result.put("statusDistribution", statusDistribution);
        result.put("statusPercentages", statusPercentages);
        result.put("totalRequests", totalRequests);
        result.put("windowMinutes", windowMinutes);

        return result;
    }

    public Map<String, Object> getLatencyMetrics(Long tenantId, String appId, Integer minutes) {
        int windowMinutes = minutes != null ? minutes : 60;
        Map<String, Object> result = new LinkedHashMap<>();

        List<String> timeWindows = generateTimeWindows(windowMinutes);
        List<Double> avgLatencies = new ArrayList<>();
        List<Double> p95Latencies = new ArrayList<>();
        List<Double> p99Latencies = new ArrayList<>();

        double overallSum = 0;
        long overallCount = 0;
        List<Double> allLatencies = new ArrayList<>();

        for (String window : timeWindows) {
            String key = buildKey(LATENCY_KEY, tenantId, appId, window);
            List<Object> rawList = redisTemplate.opsForList().range(key, 0, -1);
            List<Double> latencies = new ArrayList<>();
            if (rawList != null) {
                for (Object obj : rawList) {
                    try {
                        latencies.add(Double.parseDouble(obj.toString()));
                    } catch (NumberFormatException e) {
                    }
                }
            }

            double avg = latencies.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            avgLatencies.add(Math.round(avg * 100) / 100.0);
            p95Latencies.add(calculatePercentile(latencies, 95));
            p99Latencies.add(calculatePercentile(latencies, 99));

            overallSum += latencies.stream().mapToDouble(Double::doubleValue).sum();
            overallCount += latencies.size();
            allLatencies.addAll(latencies);
        }

        Collections.sort(allLatencies);
        double overallAvg = overallCount > 0 ? overallSum / overallCount : 0;
        double overallP95 = calculatePercentile(allLatencies, 95);
        double overallP99 = calculatePercentile(allLatencies, 99);
        double overallMin = allLatencies.isEmpty() ? 0 : allLatencies.get(0);
        double overallMax = allLatencies.isEmpty() ? 0 : allLatencies.get(allLatencies.size() - 1);

        result.put("timeWindows", timeWindows);
        result.put("avgLatencies", avgLatencies);
        result.put("p95Latencies", p95Latencies);
        result.put("p99Latencies", p99Latencies);
        result.put("overallAvg", Math.round(overallAvg * 100) / 100.0);
        result.put("overallP95", overallP95);
        result.put("overallP99", overallP99);
        result.put("overallMin", overallMin);
        result.put("overallMax", overallMax);
        result.put("totalSamples", overallCount);
        result.put("windowMinutes", windowMinutes);

        return result;
    }

    public Map<String, Object> getRecentLogs(Long tenantId, String appId, Integer limit) {
        int pageSize = limit != null ? Math.min(limit, 500) : 100;
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> logs = new ArrayList<>();

        String pattern;
        if (tenantId != null && appId != null) {
            pattern = LOGS_PREFIX + tenantId + ":" + appId + ":*";
        } else if (tenantId != null) {
            pattern = LOGS_PREFIX + tenantId + ":*";
        } else {
            pattern = LOGS_PREFIX + "*";
        }

        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null) {
            List<String> sortedKeys = keys.stream()
                    .sorted(Collections.reverseOrder())
                    .limit(pageSize)
                    .collect(Collectors.toList());

            for (String key : sortedKeys) {
                Object value = redisTemplate.opsForValue().get(key);
                if (value != null) {
                    Map<String, Object> logEntry = new LinkedHashMap<>();
                    logEntry.put("key", key);
                    logEntry.put("data", value);
                    logs.add(logEntry);
                }
            }
        }

        result.put("logs", logs);
        result.put("count", logs.size());
        result.put("limit", pageSize);

        return result;
    }

    private String buildKey(String prefix, Long tenantId, String appId, String window) {
        StringBuilder sb = new StringBuilder(prefix);
        if (tenantId != null) {
            sb.append(tenantId).append(":");
        }
        if (appId != null) {
            sb.append(appId).append(":");
        }
        sb.append(window);
        return sb.toString();
    }

    private List<String> generateTimeWindows(int minutes) {
        List<String> windows = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

        for (int i = minutes - 1; i >= 0; i--) {
            LocalDateTime windowTime = now.minusMinutes(i);
            windows.add(windowTime.format(formatter));
        }
        return windows;
    }

    private String extractStatusCode(String key) {
        int lastColon = key.lastIndexOf(':');
        return lastColon >= 0 ? key.substring(lastColon + 1) : key;
    }

    private double calculatePercentile(List<Double> sortedData, double percentile) {
        if (sortedData == null || sortedData.isEmpty()) {
            return 0;
        }
        List<Double> sorted = new ArrayList<>(sortedData);
        Collections.sort(sorted);
        int index = (int) Math.ceil(percentile / 100.0 * sorted.size());
        if (index < 1) index = 1;
        if (index > sorted.size()) index = sorted.size();
        return Math.round(sorted.get(index - 1) * 100) / 100.0;
    }
}
