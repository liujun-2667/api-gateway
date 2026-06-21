package com.apigateway.common.utils;

import com.apigateway.common.entity.RateLimitConfig;
import com.apigateway.common.exception.TooManyRequestsException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenBucketManager {

    private final ConcurrentMap<String, Bucket> localBuckets = new ConcurrentHashMap<>();
    private final RedisTemplate<String, Object> redisTemplate;
    private ProxyManager<String> proxyManager;

    public Bucket resolveBucket(String key, RateLimitConfig config) {
        Supplier<BucketConfiguration> configSupplier = () -> buildConfiguration(config);

        if (proxyManager != null) {
            return proxyManager.builder().build(key, configSupplier);
        }

        return localBuckets.computeIfAbsent(key, k -> Bucket.builder().addLimit(buildBandwidth(config)).build());
    }

    public boolean tryConsume(String key, RateLimitConfig config, long numTokens) {
        Bucket bucket = resolveBucket(key, config);
        return bucket.tryConsume(numTokens);
    }

    public void consumeOrThrow(String key, RateLimitConfig config, long numTokens) {
        Bucket bucket = resolveBucket(key, config);
        if (!bucket.tryConsume(numTokens)) {
            long waitTime = bucket.estimateAbilityToConsume(numTokens).getNanosToWaitForRefill() / 1_000_000_000;
            throw new TooManyRequestsException("Rate limit exceeded. Please try again later.", waitTime);
        }
    }

    public long getAvailableTokens(String key, RateLimitConfig config) {
        Bucket bucket = resolveBucket(key, config);
        return bucket.getAvailableTokens();
    }

    public void resetBucket(String key) {
        localBuckets.remove(key);
        if (redisTemplate != null) {
            try {
                redisTemplate.delete("bucket4j:" + key);
            } catch (Exception e) {
                log.warn("Failed to delete bucket from Redis: key={}", key, e);
            }
        }
    }

    public void setProxyManager(ProxyManager<String> proxyManager) {
        this.proxyManager = proxyManager;
    }

    private BucketConfiguration buildConfiguration(RateLimitConfig config) {
        return BucketConfiguration.builder()
                .addLimit(buildBandwidth(config))
                .build();
    }

    private Bandwidth buildBandwidth(RateLimitConfig config) {
        long capacity = config.getBurstCapacity() != null ? config.getBurstCapacity() : config.getRequestsPerSecond();
        Refill refill = Refill.intervally(config.getRequestsPerSecond(), Duration.ofSeconds(1));
        return Bandwidth.classic(capacity, refill);
    }

    public Bandwidth createBandwidthPerSecond(long limit) {
        return Bandwidth.classic(limit, Refill.intervally(limit, Duration.ofSeconds(1)));
    }

    public Bandwidth createBandwidthPerMinute(long limit) {
        return Bandwidth.classic(limit, Refill.intervally(limit, Duration.ofMinutes(1)));
    }

    public Bandwidth createBandwidthPerHour(long limit) {
        return Bandwidth.classic(limit, Refill.intervally(limit, Duration.ofHours(1)));
    }

    public Bandwidth createBandwidthPerDay(long limit) {
        return Bandwidth.classic(limit, Refill.intervally(limit, Duration.ofDays(1)));
    }
}
