package com.apigateway.gateway.service;

import com.apigateway.common.entity.ApiKey;
import com.apigateway.common.entity.Tenant;
import com.apigateway.common.enums.ApiKeyStatus;
import com.apigateway.gateway.repository.ApiKeyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantKeyService {

    private static final String CACHE_PREFIX = "gateway:apikey:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private final ApiKeyRepository apiKeyRepository;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public Mono<TenantKeyInfo> validateApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return Mono.empty();
        }

        String cacheKey = CACHE_PREFIX + apiKey;
        return redisTemplate.opsForValue().get(cacheKey)
                .flatMap(this::deserialize)
                .switchIfEmpty(loadFromDatabase(apiKey))
                .filter(this::isValid)
                .doOnNext(info -> log.debug("API Key validated: keyId={}, tenantId={}", info.getKeyId(), info.getTenantId()));
    }

    private Mono<TenantKeyInfo> loadFromDatabase(String apiKey) {
        return Mono.justOrEmpty(apiKeyRepository.findByApiKeyAndStatusWithTenant(apiKey, ApiKeyStatus.ACTIVE))
                .map(this::toTenantKeyInfo)
                .flatMap(info -> cacheApiKey(apiKey, info).thenReturn(info));
    }

    private Mono<TenantKeyInfo> deserialize(String json) {
        try {
            return Mono.just(objectMapper.readValue(json, TenantKeyInfo.class));
        } catch (Exception e) {
            log.error("Failed to deserialize cached API key info", e);
            return Mono.empty();
        }
    }

    private Mono<String> cacheApiKey(String apiKey, TenantKeyInfo info) {
        try {
            String json = objectMapper.writeValueAsString(info);
            String cacheKey = CACHE_PREFIX + apiKey;
            return redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL);
        } catch (Exception e) {
            log.error("Failed to cache API key info", e);
            return Mono.empty();
        }
    }

    private boolean isValid(TenantKeyInfo info) {
        if (info == null) {
            return false;
        }
        if (!ApiKeyStatus.ACTIVE.name().equals(info.getStatus())) {
            return false;
        }
        if (!Boolean.TRUE.equals(info.getTenantEnabled())) {
            return false;
        }
        if (info.getExpiresAt() != null && info.getExpiresAt().isBefore(LocalDateTime.now())) {
            return false;
        }
        return true;
    }

    private TenantKeyInfo toTenantKeyInfo(ApiKey apiKey) {
        Tenant tenant = apiKey.getTenant();
        return TenantKeyInfo.builder()
                .apiKeyId(apiKey.getId())
                .keyId(apiKey.getKeyId())
                .apiKey(apiKey.getApiKey())
                .tenantId(tenant != null ? tenant.getId() : null)
                .tenantName(tenant != null ? tenant.getName() : null)
                .tenantEnabled(tenant != null ? tenant.getEnabled() : false)
                .applicationId(apiKey.getApplication() != null ? apiKey.getApplication().getId() : null)
                .status(apiKey.getStatus().name())
                .expiresAt(apiKey.getExpiresAt())
                .allowedIps(apiKey.getAllowedIps())
                .rateLimitPerSecond(apiKey.getRateLimitPerSecond())
                .rateLimitPerDay(apiKey.getRateLimitPerDay())
                .build();
    }

    public Mono<Void> evictCache(String apiKey) {
        String cacheKey = CACHE_PREFIX + apiKey;
        return redisTemplate.delete(cacheKey).then();
    }
}
