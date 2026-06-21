package com.apigateway.gateway.service;

import com.apigateway.gateway.entity.ApiKeyR2dbc;
import com.apigateway.gateway.entity.TenantR2dbc;
import com.apigateway.gateway.repository.ApiKeyRepository;
import com.apigateway.gateway.repository.TenantRepository;
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
    private final TenantRepository tenantRepository;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public Mono<TenantKeyInfo> validateApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return Mono.empty();
        }

        String cacheKey = CACHE_PREFIX + apiKey;
        return redisTemplate.opsForValue().get(cacheKey)
                .flatMap(this::deserialize)
                .switchIfEmpty(Mono.defer(() -> loadFromDatabase(apiKey)))
                .filter(this::isValid)
                .doOnNext(info -> log.debug("API Key validated: keyId={}, tenantId={}", info.getKeyId(), info.getTenantId()))
                .onErrorResume(e -> {
                    log.error("Error validating API key", e);
                    return Mono.empty();
                });
    }

    private Mono<TenantKeyInfo> loadFromDatabase(String apiKey) {
        return apiKeyRepository.findByApiKey(apiKey)
                .flatMap(apiKeyEntity -> {
                    if (apiKeyEntity.getTenantId() != null) {
                        return tenantRepository.findById(apiKeyEntity.getTenantId())
                                .map(tenant -> TenantKeyInfo.from(apiKeyEntity, tenant))
                                .defaultIfEmpty(TenantKeyInfo.from(apiKeyEntity, null));
                    }
                    return Mono.just(TenantKeyInfo.from(apiKeyEntity, null));
                })
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

    private Mono<Boolean> cacheApiKey(String apiKey, TenantKeyInfo info) {
        try {
            String json = objectMapper.writeValueAsString(info);
            String cacheKey = CACHE_PREFIX + apiKey;
            return redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL);
        } catch (Exception e) {
            log.error("Failed to cache API key info", e);
            return Mono.just(false);
        }
    }

    private boolean isValid(TenantKeyInfo info) {
        if (info == null) {
            return false;
        }
        if (!"ACTIVE".equals(info.getStatus())) {
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

    public Mono<Void> evictCache(String apiKey) {
        String cacheKey = CACHE_PREFIX + apiKey;
        return redisTemplate.delete(cacheKey).then();
    }
}
