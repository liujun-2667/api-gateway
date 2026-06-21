package com.apigateway.gateway.filter;

import com.apigateway.gateway.service.CircuitBreakerService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class CircuitBreakerFilter implements GlobalFilter, Ordered {

    private static final int ORDER = -70;

    @Value("${gateway.circuit-breaker.enabled:true}")
    private boolean circuitBreakerEnabled;

    private final CircuitBreakerService circuitBreakerService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!circuitBreakerEnabled) {
            return chain.filter(exchange);
        }

        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route == null) {
            return chain.filter(exchange);
        }

        Long routeId = null;
        Long tenantId = null;
        Boolean cbEnabled = null;

        if (route.getMetadata() != null) {
            Object routeRuleIdObj = route.getMetadata().get("routeRuleId");
            if (routeRuleIdObj != null) {
                routeId = ((Number) routeRuleIdObj).longValue();
            }
            Object tenantIdObj = route.getMetadata().get("tenantId");
            if (tenantIdObj != null) {
                tenantId = ((Number) tenantIdObj).longValue();
            }
            cbEnabled = (Boolean) route.getMetadata().get("circuitBreakerEnabled");
        }

        if (Boolean.FALSE.equals(cbEnabled) || routeId == null) {
            return chain.filter(exchange);
        }

        Long finalRouteId = routeId;
        Long finalTenantId = tenantId;
        CircuitBreaker circuitBreaker = circuitBreakerService.getCircuitBreakerForRoute(finalRouteId, finalTenantId);

        if (circuitBreaker.getState() == CircuitBreaker.State.OPEN
                || circuitBreaker.getState() == CircuitBreaker.State.FORCED_OPEN) {
            log.warn("Circuit breaker is OPEN for route: {}", finalRouteId);
            return buildServiceUnavailableResponse(exchange, finalRouteId, finalTenantId);
        }

        long startTime = System.nanoTime();

        return chain.filter(exchange)
                .doOnSuccess(v -> {
                    long duration = System.nanoTime() - startTime;
                    if (!exchange.getResponse().isCommitted()) {
                        int statusCode = exchange.getResponse().getStatusCode() != null
                                ? exchange.getResponse().getStatusCode().value() : 200;
                        if (statusCode >= 500) {
                            circuitBreaker.onError(duration, java.util.concurrent.TimeUnit.NANOSECONDS,
                                    new RuntimeException("Server error: " + statusCode));
                        } else {
                            circuitBreaker.onSuccess(duration, java.util.concurrent.TimeUnit.NANOSECONDS);
                        }
                    } else {
                        circuitBreaker.onSuccess(duration, java.util.concurrent.TimeUnit.NANOSECONDS);
                    }
                })
                .doOnError(throwable -> {
                    long duration = System.nanoTime() - startTime;
                    circuitBreaker.onError(duration, java.util.concurrent.TimeUnit.NANOSECONDS, throwable);
                    log.error("Request failed for route: {}", finalRouteId, throwable);
                });
    }

    private Mono<Void> buildServiceUnavailableResponse(ServerWebExchange exchange, Long routeId, Long tenantId) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String fallbackUrl = circuitBreakerService.getFallbackUrl(routeId, tenantId);
        String body;
        if (fallbackUrl != null && !fallbackUrl.isEmpty()) {
            body = String.format("{\"code\":503,\"message\":\"Service temporarily unavailable\",\"fallbackUrl\":\"%s\"}", fallbackUrl);
        } else {
            body = "{\"code\":503,\"message\":\"Service temporarily unavailable, please try again later\"}";
        }

        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
