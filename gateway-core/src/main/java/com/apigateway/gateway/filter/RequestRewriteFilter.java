package com.apigateway.gateway.filter;

import com.apigateway.gateway.service.TenantKeyInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class RequestRewriteFilter implements GlobalFilter, Ordered {

    private static final int ORDER = -60;
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String REQUEST_TIME_HEADER = "X-Request-Time";
    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest originalRequest = exchange.getRequest();
        ServerHttpRequest.Builder requestBuilder = originalRequest.mutate();

        String requestId = java.util.UUID.randomUUID().toString();
        requestBuilder.header(REQUEST_ID_HEADER, requestId);
        exchange.getAttributes().put("requestId", requestId);

        String requestTime = String.valueOf(System.currentTimeMillis());
        requestBuilder.header(REQUEST_TIME_HEADER, requestTime);
        exchange.getAttributes().put("requestStartTime", System.nanoTime());

        String clientIp = exchange.getAttribute(TenantAuthenticationFilter.CLIENT_IP_ATTR);
        if (clientIp != null) {
            String existingForwardedFor = originalRequest.getHeaders().getFirst(FORWARDED_FOR_HEADER);
            String forwardedFor = existingForwardedFor != null && !existingForwardedFor.isEmpty()
                    ? existingForwardedFor + ", " + clientIp
                    : clientIp;
            requestBuilder.header(FORWARDED_FOR_HEADER, forwardedFor);
        }

        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route != null && route.getMetadata() != null) {
            Object routeRuleIdObj = route.getMetadata().get("routeRuleId");
            if (routeRuleIdObj != null) {
                requestBuilder.header("X-Route-Id", String.valueOf(routeRuleIdObj));
            }
            Object applicationIdObj = route.getMetadata().get("applicationId");
            if (applicationIdObj != null) {
                requestBuilder.header("X-Application-Id", String.valueOf(applicationIdObj));
            }
        }

        TenantKeyInfo keyInfo = exchange.getAttribute(TenantAuthenticationFilter.TENANT_KEY_ATTR);
        if (keyInfo != null && keyInfo.getApplicationId() != null) {
            requestBuilder.header("X-Application-Id", String.valueOf(keyInfo.getApplicationId()));
        }

        ServerHttpRequest mutatedRequest = requestBuilder.build();
        log.debug("Request rewritten: requestId={}, path={}", requestId, mutatedRequest.getURI().getPath());

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
