package com.apigateway.gateway.filter;

import com.apigateway.common.exception.TooManyRequestsException;
import com.apigateway.gateway.service.RateLimitService;
import com.apigateway.gateway.service.TenantKeyInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final int ORDER = -80;

    @Value("${gateway.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    private final RateLimitService rateLimitService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!rateLimitEnabled) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String clientIp = exchange.getAttribute(TenantAuthenticationFilter.CLIENT_IP_ATTR);
        TenantKeyInfo keyInfo = exchange.getAttribute(TenantAuthenticationFilter.TENANT_KEY_ATTR);

        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        Long routeId = null;
        Long tenantId = null;
        Boolean routeRateLimitEnabled = null;

        if (route != null && route.getMetadata() != null) {
            Object routeRuleIdObj = route.getMetadata().get("routeRuleId");
            if (routeRuleIdObj != null) {
                routeId = ((Number) routeRuleIdObj).longValue();
            }
            Object tenantIdObj = route.getMetadata().get("tenantId");
            if (tenantIdObj != null) {
                tenantId = ((Number) tenantIdObj).longValue();
            }
            routeRateLimitEnabled = (Boolean) route.getMetadata().get("rateLimitEnabled");
        }

        if (keyInfo != null && tenantId == null) {
            tenantId = keyInfo.getTenantId();
        }

        if (Boolean.FALSE.equals(routeRateLimitEnabled)) {
            return chain.filter(exchange);
        }

        Long finalTenantId = tenantId;
        Long finalRouteId = routeId;
        Long finalApiKeyId = keyInfo != null ? keyInfo.getApiKeyId() : null;

        return checkIpRateLimit(clientIp)
                .then(Mono.defer(() -> finalTenantId != null ? checkTenantRateLimit(finalTenantId, keyInfo) : Mono.empty()))
                .then(Mono.defer(() -> finalApiKeyId != null && finalTenantId != null
                        ? checkApiKeyRateLimit(finalApiKeyId, finalTenantId) : Mono.empty()))
                .then(Mono.defer(() -> finalRouteId != null ? checkRouteRateLimit(finalRouteId, finalTenantId) : Mono.empty()))
                .then(chain.filter(exchange));
    }

    private Mono<Void> checkIpRateLimit(String clientIp) {
        if (clientIp == null || clientIp.isEmpty()) {
            return Mono.empty();
        }
        return rateLimitService.tryAcquireForIp(clientIp)
                .flatMap(acquired -> {
                    if (!acquired) {
                        return Mono.error(new TooManyRequestsException("IP rate limit exceeded", 60L));
                    }
                    return Mono.empty();
                });
    }

    private Mono<Void> checkTenantRateLimit(Long tenantId, TenantKeyInfo keyInfo) {
        return rateLimitService.tryAcquireForTenant(tenantId, keyInfo)
                .flatMap(acquired -> {
                    if (!acquired) {
                        log.warn("Tenant rate limit exceeded: tenantId={}", tenantId);
                        return Mono.error(new TooManyRequestsException("Tenant rate limit exceeded", 60L));
                    }
                    return Mono.empty();
                });
    }

    private Mono<Void> checkApiKeyRateLimit(Long apiKeyId, Long tenantId) {
        return rateLimitService.tryAcquireForApiKey(apiKeyId, tenantId)
                .flatMap(acquired -> {
                    if (!acquired) {
                        log.warn("API Key rate limit exceeded: apiKeyId={}", apiKeyId);
                        return Mono.error(new TooManyRequestsException("API Key rate limit exceeded", 60L));
                    }
                    return Mono.empty();
                });
    }

    private Mono<Void> checkRouteRateLimit(Long routeId, Long tenantId) {
        return rateLimitService.tryAcquireForRoute(routeId, tenantId)
                .flatMap(acquired -> {
                    if (!acquired) {
                        log.warn("Route rate limit exceeded: routeId={}", routeId);
                        return Mono.error(new TooManyRequestsException("Route rate limit exceeded", 60L));
                    }
                    return Mono.empty();
                });
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
