package com.apigateway.gateway.filter;

import com.apigateway.gateway.metrics.MetricsCollector;
import com.apigateway.gateway.service.RouteConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class MockFilter implements GlobalFilter, Ordered {

    private static final int ORDER = -90;

    @Value("${gateway.mock.enabled:true}")
    private boolean mockFilterEnabled;

    private static final String MOCK_KEY_PREFIX = "gateway:mock:config:";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MetricsCollector metricsCollector;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!mockFilterEnabled) {
            return chain.filter(exchange);
        }

        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route == null || route.getMetadata() == null) {
            return chain.filter(exchange);
        }

        Object routeRuleIdObj = route.getMetadata().get("routeRuleId");
        if (routeRuleIdObj == null) {
            return chain.filter(exchange);
        }

        Long routeRuleId = ((Number) routeRuleIdObj).longValue();
        String mockKey = MOCK_KEY_PREFIX + routeRuleId;

        return redisTemplate.opsForHash().entries(mockKey)
                .collectList()
                .flatMap(entries -> {
                    if (entries.isEmpty()) {
                        return chain.filter(exchange);
                    }

                    Map<String, String> mockConfig = new HashMap<>();
                    for (Map.Entry<Object, Object> entry : entries) {
                        mockConfig.put(entry.getKey().toString(), entry.getValue().toString());
                    }

                    if (!"true".equals(mockConfig.get("enabled"))) {
                        return chain.filter(exchange);
                    }

                    String delayMsStr = mockConfig.get("delayMs");
                    int delayMs = 0;
                    if (delayMsStr != null) {
                        try { delayMs = Math.min(Integer.parseInt(delayMsStr), 5000); }
                        catch (NumberFormatException ignored) {}
                    }

                    String faultPercentStr = mockConfig.get("faultInjectionPercent");
                    String faultErrorCode = mockConfig.get("faultErrorCode");

                    Mono<Void> responseMono;
                    if (faultPercentStr != null && faultErrorCode != null) {
                        int faultPercent = 0;
                        try { faultPercent = Integer.parseInt(faultPercentStr); }
                        catch (NumberFormatException ignored) {}

                        if (new Random().nextInt(100) < faultPercent) {
                            responseMono = buildFaultResponse(exchange, faultErrorCode, routeRuleId);
                        } else {
                            responseMono = buildMockResponse(exchange, mockConfig.get("responseSchema"), delayMs, routeRuleId);
                        }
                    } else {
                        responseMono = buildMockResponse(exchange, mockConfig.get("responseSchema"), delayMs, routeRuleId);
                    }

                    return responseMono;
                })
                .onErrorResume(e -> {
                    log.error("Error in mock filter", e);
                    return chain.filter(exchange);
                });
    }

    private Mono<Void> buildMockResponse(ServerWebExchange exchange, String responseSchemaJson, int delayMs, Long routeRuleId) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.OK);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set("X-Mock-Response", "true");

        Object mockBody = generateMockFromSchema(responseSchemaJson);
        String bodyStr;
        try {
            bodyStr = objectMapper.writeValueAsString(mockBody);
        } catch (Exception e) {
            bodyStr = "{\"message\":\"Mock response\"}";
        }

        Long tenantId = null;
        String clientIp = exchange.getAttribute("clientIp");

        Mono<Void> writeResponse = Mono.defer(() -> {
            DataBuffer buffer = response.bufferFactory().wrap(bodyStr.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer))
                    .then(metricsCollector.recordRequest(tenantId, routeRuleId, clientIp, 200, delayMs > 0 ? delayMs : 1));
        });

        if (delayMs > 0) {
            return Mono.delay(Duration.ofMillis(delayMs)).then(writeResponse);
        }
        return writeResponse;
    }

    private Mono<Void> buildFaultResponse(ServerWebExchange exchange, String faultErrorCode, Long routeRuleId) {
        ServerHttpResponse response = exchange.getResponse();
        int statusCode;
        try {
            statusCode = Integer.parseInt(faultErrorCode);
        } catch (NumberFormatException e) {
            statusCode = 500;
        }

        response.setStatusCode(HttpStatus.valueOf(statusCode));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set("X-Mock-Response", "true");
        response.getHeaders().set("X-Mock-Fault", "true");

        String body = "{\"code\":" + statusCode + ",\"message\":\"Mock fault injection\",\"mock\":true}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));

        Long tenantId = null;
        String clientIp = exchange.getAttribute("clientIp");

        return response.writeWith(Mono.just(buffer))
                .then(metricsCollector.recordRequest(tenantId, routeRuleId, clientIp, statusCode, 0));
    }

    @SuppressWarnings("unchecked")
    private Object generateMockFromSchema(String schemaJson) {
        if (schemaJson == null || schemaJson.isEmpty()) {
            return Map.of("message", "Mock response - no schema defined");
        }
        try {
            Map<String, Object> schema = objectMapper.readValue(schemaJson, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            return generateFromSchema(schema);
        } catch (Exception e) {
            return Map.of("message", "Mock response - schema parse error");
        }
    }

    @SuppressWarnings("unchecked")
    private Object generateFromSchema(Map<String, Object> schema) {
        if (schema == null) return null;
        String type = schema.get("type") != null ? schema.get("type").toString().toLowerCase() : "object";
        Object example = schema.get("example");

        switch (type) {
            case "string":
                if (example != null) return example.toString();
                Object enumValues = schema.get("enum");
                if (enumValues instanceof List && !((List<?>) enumValues).isEmpty()) {
                    return ((List<String>) enumValues).get(0);
                }
                return "mock_" + UUID.randomUUID().toString().substring(0, 8);

            case "integer":
                if (example != null) {
                    try { return Integer.parseInt(example.toString()); }
                    catch (NumberFormatException ignored) {}
                }
                return new Random().nextInt(1000);

            case "number":
                if (example != null) {
                    try { return Double.parseDouble(example.toString()); }
                    catch (NumberFormatException ignored) {}
                }
                return Math.round(new Random().nextDouble() * 10000.0) / 100.0;

            case "boolean":
                return new Random().nextBoolean();

            case "array":
                Map<String, Object> itemsSchema = (Map<String, Object>) schema.get("items");
                if (itemsSchema != null) {
                    int count = 1 + new Random().nextInt(3);
                    List<Object> array = new ArrayList<>();
                    for (int i = 0; i < count; i++) {
                        array.add(generateFromSchema(itemsSchema));
                    }
                    return array;
                }
                return List.of("mock_item");

            case "object":
            default:
                Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
                if (properties != null && !properties.isEmpty()) {
                    Map<String, Object> result = new LinkedHashMap<>();
                    for (Map.Entry<String, Object> propEntry : properties.entrySet()) {
                        Map<String, Object> propSchema = (Map<String, Object>) propEntry.getValue();
                        result.put(propEntry.getKey(), generateFromSchema(propSchema));
                    }
                    return result;
                }
                if (example != null) return example;
                return Map.of("mock", true);
        }
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
