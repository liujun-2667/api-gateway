package com.apigateway.gateway.service;

import com.apigateway.gateway.config.RedisRateLimiterConfig;
import com.apigateway.gateway.entity.RateLimitConfigR2dbc;
import com.apigateway.gateway.repository.RateLimitConfigRepository;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    @Value("${gateway.rate-limit.default-requests-per-second:100}")
    private long defaultRequestsPerSecond;

    @Value("${gateway.rate-limit.default-burst-capacity:200}")
    private long defaultBurstCapacity;

    private final RateLimitConfigRepository rateLimitConfigRepository;
    private final ProxyManager<byte[]> proxyManager;
    private final RedisRateLimiterConfig redisRateLimiterConfig;

    private final Map<String, RateLimitConfigR2dbc> tenantConfigs = new ConcurrentHashMap<>();
    private final Map<String, RateLimitConfigR2dbc> routeConfigs = new ConcurrentHashMap<>();
    private final Map<String, RateLimitConfigR2dbc> appConfigs = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        refreshConfigs();
    }

    @Scheduled(fixedDelay = 60000)
    public void refreshConfigs() {
        rateLimitConfigRepository.findAll()
                .filter(config -> Boolean.TRUE.equals(config.getEnabled()))
                .collectList()
                .doOnSuccess(configs -> {
                    tenantConfigs.clear();
                    routeConfigs.clear();
                    appConfigs.clear();

                    for (RateLimitConfigR2dbc config : configs) {
                        String scope = config.getScope();
                        if (scope == null) {
                            continue;
                        }

                        switch (scope.toUpperCase()) {
                            case "TENANT" -> {
                                if (config.getAppId() != null) {
                                    String tenantKey = "tenant:" + config.getAppId();
                                    tenantConfigs.put(tenantKey, config);
                                }
                            }
                            case "ROUTE" -> {
                                if (config.getRuleId() != null) {
                                    String routeKey = "route:" + config.getRuleId();
                                    routeConfigs.put(routeKey, config);
                                }
                            }
                            case "APPLICATION" -> {
                                if (config.getAppId() != null) {
                                    String appKey = "app:" + config.getAppId();
                                    appConfigs.put(appKey, config);
                                }
                            }
                            default -> log.debug("Unknown rate limit scope: {}", scope);
                        }
                    }

                    log.info("Rate limit configs refreshed: tenant={}, route={}, app={}",
                            tenantConfigs.size(), routeConfigs.size(), appConfigs.size());
                })
                .doOnError(e -> log.error("Failed to refresh rate limit configs", e))
                .subscribe();
    }

    public Mono<Boolean> tryAcquire(String key, long limit, long burst) {
        return Mono.fromCallable(() -> {
            byte[] bucketKey = key.getBytes(StandardCharsets.UTF_8);
            long effectiveLimit = limit > 0 ? limit : defaultRequestsPerSecond;
            long effectiveBurst = burst > 0 ? burst : defaultBurstCapacity;

            Bucket bucket = redisRateLimiterConfig.buildBucket(
                    proxyManager, bucketKey,
                    effectiveLimit,
                    effectiveLimit * 60,
                    effectiveBurst
            );

            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            if (probe.isConsumed()) {
                return true;
            }
            log.warn("Rate limit exceeded for key: {}, wait: {}ms", key, probe.getNanosToWaitForRefill() / 1_000_000);
            return false;
        }).onErrorResume(e -> {
            log.error("Error acquiring rate limit for key: {}", key, e);
            return Mono.just(true);
        });
    }

    public Mono<Boolean> tryAcquireForTenant(Long tenantId, TenantKeyInfo keyInfo) {
        String tenantKey = "tenant:" + tenantId;
        RateLimitConfigR2dbc config = tenantConfigs.get(tenantKey);

        long limit = defaultRequestsPerSecond;
        long burst = defaultBurstCapacity;

        if (config != null) {
            limit = config.getLimitPerSecond() != null ? config.getLimitPerSecond() : defaultRequestsPerSecond;
            burst = config.getBurstCapacity() != null ? config.getBurstCapacity() : defaultBurstCapacity;
        } else if (keyInfo != null && keyInfo.getTenantMaxQps() != null) {
            limit = keyInfo.getTenantMaxQps();
            burst = keyInfo.getTenantMaxQps() * 2;
        } else if (keyInfo != null && keyInfo.getRateLimitPerSecond() != null) {
            limit = keyInfo.getRateLimitPerSecond();
            burst = keyInfo.getRateLimitPerSecond() * 2;
        }

        return tryAcquire("rl:tenant:" + tenantId, limit, burst);
    }

    public Mono<Boolean> tryAcquireForRoute(Long routeId, Long tenantId) {
        String routeKey = "route:" + routeId;
        RateLimitConfigR2dbc config = routeConfigs.get(routeKey);

        long limit = defaultRequestsPerSecond;
        long burst = defaultBurstCapacity;

        if (config != null) {
            limit = config.getLimitPerSecond() != null ? config.getLimitPerSecond() : defaultRequestsPerSecond;
            burst = config.getBurstCapacity() != null ? config.getBurstCapacity() : defaultBurstCapacity;
        }

        return tryAcquire("rl:route:" + routeId, limit, burst);
    }

    public Mono<Boolean> tryAcquireForIp(String clientIp) {
        long ipLimit = defaultRequestsPerSecond / 2;
        long ipBurst = defaultBurstCapacity / 2;
        return tryAcquire("rl:ip:" + clientIp, ipLimit, ipBurst);
    }

    public Mono<Boolean> tryAcquireForApiKey(Long apiKeyId, Long tenantId) {
        long apiKeyLimit = defaultRequestsPerSecond;
        long apiKeyBurst = defaultBurstCapacity;
        return tryAcquire("rl:apikey:" + apiKeyId, apiKeyLimit, apiKeyBurst);
    }

    public Map<String, RateLimitConfigR2dbc> getTenantConfigs() {
        return Map.copyOf(tenantConfigs);
    }

    public Map<String, RateLimitConfigR2dbc> getRouteConfigs() {
        return Map.copyOf(routeConfigs);
    }

    public Map<String, RateLimitConfigR2dbc> getAppConfigs() {
        return Map.copyOf(appConfigs);
    }
}
