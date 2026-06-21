package com.apigateway.gateway.service;

import com.apigateway.gateway.entity.CircuitBreakerConfigR2dbc;
import com.apigateway.gateway.repository.CircuitBreakerConfigRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class CircuitBreakerService {

    private final CircuitBreakerConfigRepository circuitBreakerConfigRepository;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    private final Map<String, CircuitBreakerConfigR2dbc> upstreamConfigs = new ConcurrentHashMap<>();
    private final Map<String, CircuitBreakerConfigR2dbc> appConfigs = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        refreshConfigs();
    }

    @Scheduled(fixedDelay = 60000)
    public void refreshConfigs() {
        circuitBreakerConfigRepository.findAll()
                .filter(config -> Boolean.TRUE.equals(config.getEnabled()))
                .collectList()
                .doOnSuccess(configs -> {
                    upstreamConfigs.clear();
                    appConfigs.clear();

                    for (CircuitBreakerConfigR2dbc config : configs) {
                        if (config.getUpstreamService() != null && !config.getUpstreamService().isEmpty()) {
                            String upstreamKey = "upstream:" + config.getUpstreamService();
                            upstreamConfigs.put(upstreamKey, config);
                            createOrUpdateCircuitBreaker(config.getUpstreamService(), config);
                        }
                        if (config.getAppId() != null) {
                            String appKey = "app:" + config.getAppId();
                            appConfigs.put(appKey, config);
                        }
                    }

                    log.info("Circuit breaker configs refreshed: upstream={}, app={}",
                            upstreamConfigs.size(), appConfigs.size());
                })
                .doOnError(e -> log.error("Failed to refresh circuit breaker configs", e))
                .subscribe();
    }

    private void createOrUpdateCircuitBreaker(String name, CircuitBreakerConfigR2dbc config) {
        try {
            io.github.resilience4j.circuitbreaker.CircuitBreakerConfig cbConfig = io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                    .failureRateThreshold(config.getFailureRateThreshold() != null ? config.getFailureRateThreshold() : 50.0f)
                    .slidingWindowSize(config.getSlidingWindowSize() != null ? config.getSlidingWindowSize() : 100)
                    .minimumNumberOfCalls(config.getMinimumNumberOfCalls() != null ? config.getMinimumNumberOfCalls() : 10)
                    .waitDurationInOpenState(Duration.ofMillis(config.getWaitDurationInOpenStateMs() != null ? config.getWaitDurationInOpenStateMs() : 60000))
                    .permittedNumberOfCallsInHalfOpenState(config.getPermittedNumberOfCallsInHalfOpenState() != null ? config.getPermittedNumberOfCallsInHalfOpenState() : 10)
                    .recordExceptions(Exception.class)
                    .build();

            CircuitBreaker existing = circuitBreakerRegistry.getAllCircuitBreakers()
                    .stream()
                    .filter(cb -> cb.getName().equals(name))
                    .findFirst()
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

    public CircuitBreaker getCircuitBreaker(String name) {
        return circuitBreakerRegistry.circuitBreaker(name);
    }

    public CircuitBreaker getCircuitBreakerForUpstream(String upstreamService) {
        String upstreamKey = "upstream:" + upstreamService;
        if (upstreamConfigs.containsKey(upstreamKey)) {
            return circuitBreakerRegistry.circuitBreaker(upstreamService);
        }
        return getDefaultCircuitBreaker();
    }

    public CircuitBreaker getCircuitBreakerForRoute(Long routeId, Long appId) {
        if (appId != null) {
            String appKey = "app:" + appId;
            CircuitBreakerConfigR2dbc config = appConfigs.get(appKey);
            if (config != null && config.getUpstreamService() != null) {
                return circuitBreakerRegistry.circuitBreaker(config.getUpstreamService());
            }
        }
        return getDefaultCircuitBreaker();
    }

    private CircuitBreaker getDefaultCircuitBreaker() {
        return circuitBreakerRegistry.circuitBreaker("default");
    }

    public Mono<Boolean> isCircuitBreakerOpen(String name) {
        return Mono.fromCallable(() -> {
            CircuitBreaker circuitBreaker = getCircuitBreaker(name);
            CircuitBreaker.State state = circuitBreaker.getState();
            log.debug("Circuit breaker state for {}: {}", name, state);
            return state == CircuitBreaker.State.OPEN || state == CircuitBreaker.State.FORCED_OPEN;
        });
    }

    public <T> Mono<T> executeWithCircuitBreaker(String name, Mono<T> mono, Mono<T> fallback) {
        CircuitBreaker circuitBreaker = getCircuitBreaker(name);
        return mono
                .transform(CircuitBreakerOperator.of(circuitBreaker))
                .onErrorResume(throwable -> {
                    if (throwable instanceof io.github.resilience4j.circuitbreaker.CallNotPermittedException) {
                        log.warn("Circuit breaker is OPEN for: {}", name);
                        return fallback != null ? fallback : Mono.error(throwable);
                    }
                    return Mono.error(throwable);
                });
    }

    public void recordSuccess(String name, long durationNanos) {
        try {
            CircuitBreaker circuitBreaker = getCircuitBreaker(name);
            circuitBreaker.onSuccess(durationNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
        } catch (Exception e) {
            log.debug("Error recording success for circuit breaker: {}", name, e);
        }
    }

    public void recordFailure(String name, long durationNanos, Throwable throwable) {
        try {
            CircuitBreaker circuitBreaker = getCircuitBreaker(name);
            circuitBreaker.onError(durationNanos, java.util.concurrent.TimeUnit.NANOSECONDS, throwable);
        } catch (Exception e) {
            log.debug("Error recording failure for circuit breaker: {}", name, e);
        }
    }

    public String getFallbackResponseBody(String name) {
        String upstreamKey = "upstream:" + name;
        CircuitBreakerConfigR2dbc config = upstreamConfigs.get(upstreamKey);
        if (config != null && config.getFallbackResponseBody() != null && !config.getFallbackResponseBody().isEmpty()) {
            return config.getFallbackResponseBody();
        }
        return null;
    }

    public String getFallbackResponseBodyForRoute(Long routeId, Long appId) {
        if (appId != null) {
            String appKey = "app:" + appId;
            CircuitBreakerConfigR2dbc config = appConfigs.get(appKey);
            if (config != null && config.getFallbackResponseBody() != null) {
                return config.getFallbackResponseBody();
            }
        }
        return null;
    }

    public Map<String, CircuitBreakerConfigR2dbc> getUpstreamConfigs() {
        return Map.copyOf(upstreamConfigs);
    }

    public Map<String, CircuitBreakerConfigR2dbc> getAppConfigs() {
        return Map.copyOf(appConfigs);
    }

    public CircuitBreaker.State getCircuitBreakerState(String name) {
        return getCircuitBreaker(name).getState();
    }
}
