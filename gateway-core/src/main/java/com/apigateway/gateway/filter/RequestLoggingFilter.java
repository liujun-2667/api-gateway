package com.apigateway.gateway.filter;

import com.apigateway.gateway.metrics.MetricsCollector;
import com.apigateway.gateway.service.TenantKeyInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final int ORDER = Ordered.LOWEST_PRECEDENCE;

    @Value("${gateway.request-logging.enabled:true}")
    private boolean requestLoggingEnabled;

    @Value("${gateway.request-logging.redis-key-prefix:gateway:request:log:}")
    private String redisKeyPrefix;

    private static final Duration LOG_TTL = Duration.ofDays(7);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MetricsCollector metricsCollector;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!requestLoggingEnabled) {
            return chain.filter(exchange);
        }

        return chain.filter(exchange)
                .then(Mono.defer(() -> logRequest(exchange)))
                .onErrorResume(e -> {
                    log.error("Error in request logging filter", e);
                    return Mono.empty();
                });
    }

    private Mono<Void> logRequest(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();

        Long startTimeNano = exchange.getAttribute("requestStartTime");
        long latencyMs = startTimeNano != null
                ? (System.nanoTime() - startTimeNano) / 1_000_000
                : 0;

        String requestId = exchange.getAttribute("requestId");
        String clientIp = exchange.getAttribute(TenantAuthenticationFilter.CLIENT_IP_ATTR);
        TenantKeyInfo keyInfo = exchange.getAttribute(TenantAuthenticationFilter.TENANT_KEY_ATTR);
        String trafficTag = exchange.getAttribute("trafficTag");

        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        Long routeId = null;
        Long tenantId = null;
        String routeRuleId = null;

        if (route != null) {
            if (route.getMetadata() != null) {
                Object routeRuleIdObj = route.getMetadata().get("routeRuleId");
                if (routeRuleIdObj != null) {
                    routeId = ((Number) routeRuleIdObj).longValue();
                    routeRuleId = route.getId();
                }
                Object tenantIdObj = route.getMetadata().get("tenantId");
                if (tenantIdObj != null) {
                    tenantId = ((Number) tenantIdObj).longValue();
                }
            }
        }

        if (keyInfo != null && tenantId == null) {
            tenantId = keyInfo.getTenantId();
        }

        int statusCode = exchange.getResponse().getStatusCode() != null
                ? exchange.getResponse().getStatusCode().value()
                : 0;

        Long finalTenantId = tenantId;
        Long finalRouteId = routeId;

        Map<String, Object> logEntry = new HashMap<>();
        logEntry.put("requestId", requestId);
        logEntry.put("timestamp", System.currentTimeMillis());
        logEntry.put("method", request.getMethod() != null ? request.getMethod().name() : null);
        logEntry.put("path", request.getURI().getPath());
        logEntry.put("query", request.getURI().getQuery());
        logEntry.put("statusCode", statusCode);
        logEntry.put("latencyMs", latencyMs);
        logEntry.put("clientIp", clientIp);
        logEntry.put("tenantId", finalTenantId);
        logEntry.put("routeId", finalRouteId);
        logEntry.put("routeRuleId", routeRuleId);
        logEntry.put("trafficTag", trafficTag);
        logEntry.put("userAgent", request.getHeaders().getFirst("User-Agent"));
        logEntry.put("referer", request.getHeaders().getFirst("Referer"));

        if (keyInfo != null) {
            logEntry.put("keyId", keyInfo.getKeyId());
            logEntry.put("apiKeyId", keyInfo.getApiKeyId());
        }

        return saveToRedis(logEntry)
                .then(metricsCollector.recordRequest(finalTenantId, finalRouteId, clientIp, statusCode, latencyMs))
                .doOnError(e -> log.debug("Failed to save request log", e));
    }

    private Mono<Void> saveToRedis(Map<String, Object> logEntry) {
        try {
            String json = objectMapper.writeValueAsString(logEntry);
            String dateKey = java.time.LocalDate.now().toString();
            String listKey = redisKeyPrefix + dateKey;
            String detailKey = redisKeyPrefix + "detail:" + logEntry.get("requestId");

            return Mono.when(
                    redisTemplate.opsForList().rightPush(listKey, json)
                            .flatMap(v -> redisTemplate.expire(listKey, LOG_TTL)),
                    redisTemplate.opsForValue().set(detailKey, json, LOG_TTL)
            );
        } catch (Exception e) {
            log.debug("Failed to serialize request log", e);
            return Mono.empty();
        }
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
