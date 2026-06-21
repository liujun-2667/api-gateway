package com.apigateway.gateway.metrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsCollector {

    @Value("${gateway.metrics.redis-key-prefix:gateway:metrics:}")
    private String redisKeyPrefix;

    private static final Duration METRICS_TTL = Duration.ofHours(25);

    private final ReactiveStringRedisTemplate redisTemplate;

    public Mono<Void> recordRequest(Long tenantId, Long routeId, String clientIp, int statusCode, long latencyMs) {
        String dateKey = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String minuteKey = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));

        return Mono.when(
                incrementCounter(buildQpsKey(dateKey, tenantId, routeId)),
                incrementCounter(buildQpsKey(minuteKey, tenantId, routeId)),
                recordLatency(buildLatencyKey(dateKey, tenantId, routeId), latencyMs),
                recordLatency(buildLatencyKey(minuteKey, tenantId, routeId), latencyMs),
                incrementCounter(buildStatusKey(dateKey, tenantId, routeId, statusCode)),
                incrementCounter(buildStatusKey(minuteKey, tenantId, routeId, statusCode)),
                incrementCounter(buildIpKey(dateKey, clientIp)),
                incrementCounter(buildTenantKey(dateKey, tenantId))
        ).doOnError(e -> log.error("Failed to record metrics", e));
    }

    private Mono<Void> incrementCounter(String key) {
        return redisTemplate.opsForValue().increment(key)
                .flatMap(v -> redisTemplate.expire(key, METRICS_TTL))
                .onErrorResume(e -> {
                    log.debug("Failed to increment counter: {}", key, e);
                    return Mono.empty();
                })
                .then();
    }

    private Mono<Void> recordLatency(String key, long latencyMs) {
        String latencyStr = String.valueOf(latencyMs);
        return redisTemplate.opsForList().rightPush(key, latencyStr)
                .flatMap(v -> redisTemplate.expire(key, METRICS_TTL))
                .flatMap(v -> redisTemplate.opsForList().trim(key, -1000, -1))
                .onErrorResume(e -> {
                    log.debug("Failed to record latency: {}", key, e);
                    return Mono.empty();
                })
                .then();
    }

    private String buildQpsKey(String timeKey, Long tenantId, Long routeId) {
        StringBuilder sb = new StringBuilder(redisKeyPrefix).append("qps:").append(timeKey);
        if (tenantId != null) {
            sb.append(":tenant:").append(tenantId);
        }
        if (routeId != null) {
            sb.append(":route:").append(routeId);
        }
        return sb.toString();
    }

    private String buildLatencyKey(String timeKey, Long tenantId, Long routeId) {
        StringBuilder sb = new StringBuilder(redisKeyPrefix).append("latency:").append(timeKey);
        if (tenantId != null) {
            sb.append(":tenant:").append(tenantId);
        }
        if (routeId != null) {
            sb.append(":route:").append(routeId);
        }
        return sb.toString();
    }

    private String buildStatusKey(String timeKey, Long tenantId, Long routeId, int statusCode) {
        StringBuilder sb = new StringBuilder(redisKeyPrefix).append("status:").append(timeKey);
        if (tenantId != null) {
            sb.append(":tenant:").append(tenantId);
        }
        if (routeId != null) {
            sb.append(":route:").append(routeId);
        }
        sb.append(":code:").append(statusCode / 100).append("xx");
        return sb.toString();
    }

    private String buildIpKey(String timeKey, String clientIp) {
        return redisKeyPrefix + "ip:" + timeKey + ":" + clientIp;
    }

    private String buildTenantKey(String timeKey, Long tenantId) {
        if (tenantId == null) {
            return redisKeyPrefix + "tenant:" + timeKey + ":unknown";
        }
        return redisKeyPrefix + "tenant:" + timeKey + ":" + tenantId;
    }
}
