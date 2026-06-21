package com.apigateway.gateway.filter;

import com.apigateway.gateway.service.TenantKeyInfo;
import com.apigateway.gateway.service.TrafficColorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class TrafficColoringFilter implements GlobalFilter, Ordered {

    private static final int ORDER = -90;
    private static final String TRAFFIC_TAG_HEADER = "X-Traffic-Tag";

    private final TrafficColorService trafficColorService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        TenantKeyInfo keyInfo = exchange.getAttribute(TenantAuthenticationFilter.TENANT_KEY_ATTR);
        Long tenantId = keyInfo != null ? keyInfo.getTenantId() : null;

        String colorTag = trafficColorService.matchAndGetTag(request, tenantId);
        if (colorTag != null && !colorTag.isEmpty()) {
            log.debug("Adding traffic color tag: {} for request: {}", colorTag, request.getURI());
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header(TRAFFIC_TAG_HEADER, colorTag)
                    .build();
            exchange.getAttributes().put("trafficTag", colorTag);
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
