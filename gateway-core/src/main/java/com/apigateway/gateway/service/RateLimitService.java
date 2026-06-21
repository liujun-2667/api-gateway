package com.apigateway.gateway.service;

import com.apigateway.common.entity.RateLimitConfig;
import com.apigateway.common.enums.RuleStatus;
import com.apigateway.gateway.config.RedisRateLimiterConfig;
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
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    @Value("${gateway.rate-limit.default-requests-per-second:100}")
    private long defaultRequestsPerSecond;

    @Value("${gateway.rate-limit.default-requests-per-minute:1000}")
    private long defaultRequestsPerMinute;

    @Value("${gateway.rate-limit.default-burst-capacity:200}")
    private long defaultBurstCapacity;

    private final RateLimitConfigRepository rateLimitConfigRepository;
    private final ProxyManager<byte[]> proxyManager;
    private final RedisRateLimiterConfig redisRateLimiterConfig;

    private final Map<String, RateLimitConfig> tenantConfigs = new ConcurrentHashMap<>();
    private final Map<String, RateLimitConfig> routeConfigs = new ConcurrentHashMap<>();
    private final Map<String, RateLimitConfig> apiKeyConfigs = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        refreshConfigs();
    }

    @Scheduled(fixedDelay = 60000)
    public void refreshConfigs() {
        try {
            List<RateLimitConfig> configs = rateLimitConfigRepository.findAllEnabledWithTenant();
            tenantConfigs.clear();
            routeConfigs.clear();
            apiKeyConfigs.clear();
            for (RateLimitConfig config : configs) {
                if (config.getTenant() != null) {
                    String tenantKey = "tenant:" + config.getTenant().getId();
                    tenantConfigs.put(tenantKey, config);
                }
                if (config.getRouteRule() != null) {
                    String routeKey = "route:" + config.getRouteRule().getId();
                    routeConfigs.put(routeKey, config);
                }
                if (config.getApiKey() != null) {
                    String apiKeyKey = "apikey:" + config.getApiKey().getId();
                    apiKeyConfigs.put(apiKeyKey, config);
                }
            }
            log.info("Rate limit configs refreshed: tenant={}, route={}, apikey={}",
                    tenantConfigs.size(), routeConfigs.size(), apiKeyConfigs.size());
        } catch (Exception e) {
            log.error("Failed to refresh rate limit configs", e);
        }
    }

    public Mono<Boolean> tryAcquire(String key, long limitPerSecond, long limitPerMinute, long burstCapacity) {
        return Mono.fromCallable(() -> {
            byte[] bucketKey = key.getBytes(StandardCharsets.UTF_8);
            Bucket bucket = redisRateLimiterConfig.buildBucket(
                    proxyManager, bucketKey,
                    limitPerSecond > 0 ? limitPerSecond : defaultRequestsPerSecond,
                    limitPerMinute > 0 ? limitPerMinute : defaultRequestsPerMinute,
                    burstCapacity > 0 ? burstCapacity : defaultBurstCapacity
            );
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            if (probe.isConsumed()) {
                return true;
            }
            log.warn("Rate limit exceeded for key: {}, wait: {}ms", key, probe.getNanosToWaitForRefill() / 1_000_000);
            return false;
        });
    }

    public Mono<Boolean> tryAcquireForTenant(Long tenantId, TenantKeyInfo keyInfo) {
        String tenantKey = "tenant:" + tenantId;
        RateLimitConfig config = tenantConfigs.get(tenantKey);

        long rps = defaultRequestsPerSecond;
        long rpm = defaultRequestsPerMinute;
        long burst = defaultBurstCapacity;

        if (config != null) {
            rps = config.getRequestsPerSecond();
            rpm = config.getRequestsPerMinute();
            burst = config.getBurstCapacity();
        } else if (keyInfo != null && keyInfo.getRateLimitPerSecond() != null) {
            rps = keyInfo.getRateLimitPerSecond();
            rpm = keyInfo.getRateLimitPerDay() != null ? keyInfo.getRateLimitPerDay() / 1440 : rps * 60;
            burst = rps * 2;
        }

        return tryAcquire("rl:tenant:" + tenantId, rps, rpm, burst);
    }

    public Mono<Boolean> tryAcquireForRoute(Long routeId, Long tenantId) {
        String routeKey = "route:" + routeId;
        RateLimitConfig config = routeConfigs.get(routeKey);

        long rps = defaultRequestsPerSecond;
        long rpm = defaultRequestsPerMinute;
        long burst = defaultBurstCapacity;

        if (config == null && tenantId != null) {
            config = rateLimitConfigRepository.findByTenantIdAndRouteRuleIdAndEnabled(tenantId, routeId).orElse(null);
            if (config != null) {
                routeConfigs.put(routeKey, config);
            }
        }

        if (config != null) {
            rps = config.getRequestsPerSecond();
            rpm = config.getRequestsPerMinute();
            burst = config.getBurstCapacity();
        }

        return tryAcquire("rl:route:" + routeId, rps, rpm, burst);
    }

    public Mono<Boolean> tryAcquireForApiKey(Long apiKeyId, Long tenantId) {
        String apiKeyCacheKey = "apikey:" + apiKeyId;
        RateLimitConfig config = apiKeyConfigs.get(apiKeyCacheKey);

        long rps = defaultRequestsPerSecond;
        long rpm = defaultRequestsPerMinute;
        long burst = defaultBurstCapacity;

        if (config == null && tenantId != null) {
            config = rateLimitConfigRepository.findByTenantIdAndApiKeyIdAndEnabled(tenantId, apiKeyId).orElse(null);
            if (config != null) {
                apiKeyConfigs.put(apiKeyCacheKey, config);
            }
        }

        if (config != null) {
            rps = config.getRequestsPerSecond();
            rpm = config.getRequestsPerMinute();
            burst = config.getBurstCapacity();
        }

        return tryAcquire("rl:apikey:" + apiKeyId, rps, rpm, burst);
    }

    public Mono<Boolean> tryAcquireForIp(String clientIp) {
        return tryAcquire("rl:ip:" + clientIp, defaultRequestsPerSecond / 2, defaultRequestsPerMinute / 2, defaultBurstCapacity / 2);
    }
}
