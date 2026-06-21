package com.apigateway.gateway.service;

import com.apigateway.common.entity.CircuitBreakerConfig;
import com.apigateway.gateway.repository.CircuitBreakerConfigRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class CircuitBreakerService {

    private final CircuitBreakerConfigRepository circuitBreakerConfigRepository;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    private final Map<String, CircuitBreakerConfig> routeConfigs = new ConcurrentHashMap<>();
    private final Map<String, CircuitBreakerConfig> tenantConfigs = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        refreshConfigs();
    }

    @Scheduled(fixedDelay = 60000)
    public void refreshConfigs() {
        try {
            List<CircuitBreakerConfig> configs = circuitBreakerConfigRepository.findAllEnabledWithTenant();
            routeConfigs.clear();
            tenantConfigs.clear();
            for (CircuitBreakerConfig config : configs) {
                if (config.getRouteRule() != null) {
                    String routeKey = "route:" + config.getRouteRule().getId();
                    routeConfigs.put(routeKey, config);
                    createOrUpdateCircuitBreaker(routeKey, config);
                }
                if (config.getTenant() != null) {
                    String tenantKey = "tenant:" + config.getTenant().getId();
                    tenantConfigs.put(tenantKey, config);
                    createOrUpdateCircuitBreaker(tenantKey, config);
                }
            }
            log.info("Circuit breaker configs refreshed: tenant={}, route={}",
                    tenantConfigs.size(), routeConfigs.size());
        } catch (Exception e) {
            log.error("Failed to refresh circuit breaker configs", e);
        }
    }

    private void createOrUpdateCircuitBreaker(String name, CircuitBreakerConfig config) {
        try {
            io.github.resilience4j.circuitbreaker.CircuitBreakerConfig cbConfig = io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                    .failureRateThreshold(config.getFailureRateThreshold())
                    .slowCallRateThreshold(config.getSlowCallRateThreshold())
                    .slowCallDurationThreshold(Duration.ofMillis(config.getSlowCallDurationThreshold()))
                    .permittedNumberOfCallsInHalfOpenState(config.getPermittedNumberOfCallsInHalfOpenState())
                    .slidingWindowSize(config.getSlidingWindowSize())
                    .slidingWindowType(io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType.valueOf(config.getSlidingWindowType().toUpperCase()))
                    .minimumNumberOfCalls(config.getMinimumNumberOfCalls())
                    .waitDurationInOpenState(Duration.ofMillis(config.getWaitDurationInOpenState()))
                    .recordExceptions(Exception.class)
                    .build();

            CircuitBreaker existing = circuitBreakerRegistry.getAllCircuitBreakers()
                    .find(cb -> cb.getName().equals(name))
                    .orElse(null);

            if (existing == null) {
                circuitBreakerRegistry.circuitBreaker(name, cbConfig);
                log.info("Created circuit breaker: {}", name);
            } else {
                existing.transitionToClosedState();
                log.info("Circuit breaker already exists, reset to closed: {}", name);
            }
        } catch (Exception e) {
            log.error("Failed to create/update circuit breaker: {}", name, e);
        }
    }

    public CircuitBreaker getCircuitBreakerForRoute(Long routeId, Long tenantId) {
        String routeKey = "route:" + routeId;
        CircuitBreakerConfig config = routeConfigs.get(routeKey);
        if (config == null && tenantId != null) {
            config = circuitBreakerConfigRepository.findByTenantIdAndRouteRuleIdAndEnabled(tenantId, routeId).orElse(null);
            if (config != null) {
                routeConfigs.put(routeKey, config);
                createOrUpdateCircuitBreaker(routeKey, config);
            }
        }
        if (config != null) {
            return circuitBreakerRegistry.circuitBreaker(routeKey);
        }
        String tenantKey = "tenant:" + tenantId;
        if (tenantConfigs.containsKey(tenantKey)) {
            return circuitBreakerRegistry.circuitBreaker(tenantKey);
        }
        return circuitBreakerRegistry.circuitBreaker("default");
    }

    public Mono<Boolean> isCircuitBreakerOpen(Long routeId, Long tenantId) {
        return Mono.fromCallable(() -> {
            CircuitBreaker circuitBreaker = getCircuitBreakerForRoute(routeId, tenantId);
            CircuitBreaker.State state = circuitBreaker.getState();
            log.debug("Circuit breaker state for route {}: {}", routeId, state);
            return state == CircuitBreaker.State.OPEN || state == CircuitBreaker.State.FORCED_OPEN;
        });
    }

    public void recordSuccess(Long routeId, Long tenantId) {
        CircuitBreaker circuitBreaker = getCircuitBreakerForRoute(routeId, tenantId);
        circuitBreaker.onSuccess(0, java.util.concurrent.TimeUnit.NANOSECONDS);
    }

    public void recordFailure(Long routeId, Long tenantId, Throwable throwable) {
        CircuitBreaker circuitBreaker = getCircuitBreakerForRoute(routeId, tenantId);
        circuitBreaker.onError(0, java.util.concurrent.TimeUnit.NANOSECONDS, throwable);
    }

    public String getFallbackUrl(Long routeId, Long tenantId) {
        String routeKey = "route:" + routeId;
        CircuitBreakerConfig config = routeConfigs.get(routeKey);
        if (config != null && config.getFallbackUrl() != null && !config.getFallbackUrl().isEmpty()) {
            return config.getFallbackUrl();
        }
        if (tenantId != null) {
            String tenantKey = "tenant:" + tenantId;
            config = tenantConfigs.get(tenantKey);
            if (config != null) {
                return config.getFallbackUrl();
            }
        }
        return null;
    }
}
