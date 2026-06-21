package com.apigateway.gateway.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
public class RedisRateLimiterConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    @Value("${gateway.rate-limit.default-requests-per-second:100}")
    private long defaultRequestsPerSecond;

    @Value("${gateway.rate-limit.default-requests-per-minute:1000}")
    private long defaultRequestsPerMinute;

    @Value("${gateway.rate-limit.default-burst-capacity:200}")
    private long defaultBurstCapacity;

    @Bean
    public RedisClient redisClient() {
        RedisURI.Builder builder = RedisURI.builder()
                .withHost(redisHost)
                .withPort(redisPort)
                .withDatabase(redisDatabase);
        if (redisPassword != null && !redisPassword.isEmpty()) {
            builder.withPassword(redisPassword.toCharArray());
        }
        log.info("Redis rate limiter client initialized: host={}, port={}, database={}", redisHost, redisPort, redisDatabase);
        return RedisClient.create(builder.build());
    }

    @Bean
    public ProxyManager<byte[]> bucket4jProxyManager(RedisClient redisClient) {
        StatefulRedisConnection<byte[], byte[]> connection = redisClient.connect(new ByteArrayCodec());
        ProxyManager<byte[]> proxyManager = LettuceBasedProxyManager.builderFor(connection)
                .withExpirationStrategy(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofHours(1)))
                .build();
        log.info("Bucket4j Lettuce proxy manager initialized");
        return proxyManager;
    }

    @Bean
    public BucketConfiguration defaultBucketConfiguration() {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(defaultBurstCapacity)
                        .refillGreedy(defaultRequestsPerSecond, Duration.ofSeconds(1))
                        .build())
                .addLimit(Bandwidth.builder()
                        .capacity(defaultRequestsPerMinute)
                        .refillGreedy(defaultRequestsPerMinute, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    public Bucket buildBucket(ProxyManager<byte[]> proxyManager, byte[] key, long requestsPerSecond, long requestsPerMinute, long burstCapacity) {
        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(burstCapacity)
                        .refillGreedy(requestsPerSecond, Duration.ofSeconds(1))
                        .build())
                .addLimit(Bandwidth.builder()
                        .capacity(requestsPerMinute)
                        .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
                        .build())
                .build();
        return proxyManager.builder().build(key, () -> configuration);
    }
}
