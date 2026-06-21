package com.apigateway.gateway.filter;

import com.apigateway.common.exception.UnauthorizedException;
import com.apigateway.common.utils.IpUtils;
import com.apigateway.gateway.service.TenantKeyInfo;
import com.apigateway.gateway.service.TenantKeyService;
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
public class TenantAuthenticationFilter implements GlobalFilter, Ordered {

    private static final int ORDER = -100;
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String TENANT_ID_HEADER = "X-Tenant-Id";
    private static final String TENANT_NAME_HEADER = "X-Tenant-Name";
    private static final String API_KEY_ID_HEADER = "X-API-Key-Id";
    public static final String TENANT_KEY_ATTR = "tenantKeyInfo";
    public static final String CLIENT_IP_ATTR = "clientIp";

    private final TenantKeyService tenantKeyService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String clientIp = extractClientIp(request);
        exchange.getAttributes().put(CLIENT_IP_ATTR, clientIp);

        String apiKey = request.getHeaders().getFirst(API_KEY_HEADER);
        if (apiKey == null || apiKey.isEmpty()) {
            Boolean requiresAuth = (Boolean) exchange.getAttribute("requiresAuth");
            if (Boolean.TRUE.equals(requiresAuth)) {
                return Mono.error(new UnauthorizedException("X-API-Key header is required"));
            }
            return chain.filter(exchange);
        }

        return tenantKeyService.validateApiKey(apiKey)
                .switchIfEmpty(Mono.error(new UnauthorizedException("Invalid or expired API Key")))
                .flatMap(keyInfo -> {
                    if (!isIpAllowed(clientIp, keyInfo.getAllowedIps())) {
                        log.warn("IP not allowed: ip={}, keyId={}", clientIp, keyInfo.getKeyId());
                        return Mono.error(new UnauthorizedException("IP address not allowed"));
                    }

                    exchange.getAttributes().put(TENANT_KEY_ATTR, keyInfo);

                    ServerHttpRequest mutatedRequest = request.mutate()
                            .header(TENANT_ID_HEADER, String.valueOf(keyInfo.getTenantId()))
                            .header(TENANT_NAME_HEADER, keyInfo.getTenantName())
                            .header(API_KEY_ID_HEADER, keyInfo.getKeyId())
                            .build();

                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                });
    }

    private String extractClientIp(ServerHttpRequest request) {
        String[] ipHeaders = {
                "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP",
                "WL-Proxy-Client-IP", "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR"
        };
        for (String header : ipHeaders) {
            String ip = request.getHeaders().getFirst(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        }
        return request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }

    private boolean isIpAllowed(String clientIp, String allowedIps) {
        return IpUtils.isIpAllowed(clientIp, allowedIps);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
