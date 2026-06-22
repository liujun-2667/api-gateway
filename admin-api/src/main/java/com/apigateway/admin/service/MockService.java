package com.apigateway.admin.service;

import com.apigateway.admin.repository.ApiEndpointRepository;
import com.apigateway.admin.repository.MockConfigRepository;
import com.apigateway.admin.repository.RouteRuleRepository;
import com.apigateway.common.dto.ApiDocDTO;
import com.apigateway.common.entity.ApiEndpoint;
import com.apigateway.common.entity.MockConfig;
import com.apigateway.common.entity.RouteRule;
import com.apigateway.common.enums.OperationType;
import com.apigateway.common.exception.BusinessException;
import com.apigateway.common.exception.NotFoundException;
import com.apigateway.common.utils.ApiKeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MockService {

    private final MockConfigRepository mockConfigRepository;
    private final ApiEndpointRepository apiEndpointRepository;
    private final RouteRuleRepository routeRuleRepository;

    @Transactional
    public ApiDocDTO.MockConfigResponse updateMockConfig(Long endpointId, ApiDocDTO.MockConfigUpdateRequest request, String updatedBy) {
        ApiEndpoint endpoint = apiEndpointRepository.findById(endpointId)
                .orElseThrow(() -> new NotFoundException("ApiEndpoint", endpointId.toString()));

        MockConfig mockConfig = mockConfigRepository.findByEndpointId(endpointId)
                .orElseGet(() -> MockConfig.builder()
                        .mockConfigId("mock_" + ApiKeyGenerator.generateKeyId())
                        .endpoint(endpoint)
                        .enabled(false)
                        .delayMs(0)
                        .faultInjectionPercent(0)
                        .faultErrorCode(null)
                        .createdBy(updatedBy)
                        .build());

        if (request.getEnabled() != null) mockConfig.setEnabled(request.getEnabled());
        if (request.getDelayMs() != null) {
            if (request.getDelayMs() < 0 || request.getDelayMs() > 5000) {
                throw new BusinessException("Delay must be between 0 and 5000 ms");
            }
            mockConfig.setDelayMs(request.getDelayMs());
        }
        if (request.getFaultInjectionPercent() != null) {
            if (request.getFaultInjectionPercent() < 0 || request.getFaultInjectionPercent() > 100) {
                throw new BusinessException("Fault injection percent must be between 0 and 100");
            }
            mockConfig.setFaultInjectionPercent(request.getFaultInjectionPercent());
        }
        if (request.getFaultErrorCode() != null) mockConfig.setFaultErrorCode(request.getFaultErrorCode());

        mockConfig = mockConfigRepository.save(mockConfig);
        return toMockConfigResponse(mockConfig);
    }

    @Transactional(readOnly = true)
    public ApiDocDTO.MockConfigResponse getMockConfig(Long endpointId) {
        MockConfig mockConfig = mockConfigRepository.findByEndpointId(endpointId)
                .orElseThrow(() -> new NotFoundException("MockConfig", "endpoint:" + endpointId));
        return toMockConfigResponse(mockConfig);
    }

    @Transactional
    public ApiDocDTO.MockConfigResponse bindRouteRule(Long endpointId, Long routeRuleId) {
        ApiEndpoint endpoint = apiEndpointRepository.findById(endpointId)
                .orElseThrow(() -> new NotFoundException("ApiEndpoint", endpointId.toString()));
        RouteRule routeRule = routeRuleRepository.findById(routeRuleId)
                .orElseThrow(() -> new NotFoundException("RouteRule", routeRuleId.toString()));

        MockConfig mockConfig = mockConfigRepository.findByEndpointId(endpointId)
                .orElseGet(() -> MockConfig.builder()
                        .mockConfigId("mock_" + ApiKeyGenerator.generateKeyId())
                        .endpoint(endpoint)
                        .enabled(false)
                        .delayMs(0)
                        .faultInjectionPercent(0)
                        .createdBy("system")
                        .build());

        mockConfig.setRouteRule(routeRule);
        mockConfig = mockConfigRepository.save(mockConfig);
        return toMockConfigResponse(mockConfig);
    }

    @Transactional(readOnly = true)
    public boolean isMockEnabledForRoute(Long routeRuleId) {
        return mockConfigRepository.findByRouteRuleId(routeRuleId)
                .map(MockConfig::getEnabled)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public MockConfig getMockConfigForRoute(Long routeRuleId) {
        return mockConfigRepository.findByRouteRuleId(routeRuleId).orElse(null);
    }

    @Transactional(readOnly = true)
    public Optional<MockConfig> getMockConfigForEndpoint(Long endpointId) {
        return mockConfigRepository.findByEndpointId(endpointId);
    }

    @SuppressWarnings("unchecked")
    public Object generateMockResponse(ApiEndpoint endpoint) {
        Map<String, Object> responseSchema = endpoint.getResponseSchema();
        if (responseSchema == null || responseSchema.isEmpty()) {
            return Map.of("message", "Mock response - no schema defined");
        }
        return generateFromSchema(responseSchema);
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
                return "mock_string_" + UUID.randomUUID().toString().substring(0, 8);

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

    public boolean shouldInjectFault(MockConfig mockConfig) {
        if (mockConfig.getFaultInjectionPercent() == null || mockConfig.getFaultInjectionPercent() <= 0) {
            return false;
        }
        int rand = new Random().nextInt(100);
        return rand < mockConfig.getFaultInjectionPercent();
    }

    private ApiDocDTO.MockConfigResponse toMockConfigResponse(MockConfig mc) {
        return ApiDocDTO.MockConfigResponse.builder()
                .id(mc.getId())
                .mockConfigId(mc.getMockConfigId())
                .endpointId(mc.getEndpoint() != null ? mc.getEndpoint().getId() : null)
                .routeRuleId(mc.getRouteRule() != null ? mc.getRouteRule().getId() : null)
                .routeRuleName(mc.getRouteRule() != null ? mc.getRouteRule().getName() : null)
                .enabled(mc.getEnabled())
                .delayMs(mc.getDelayMs())
                .faultInjectionPercent(mc.getFaultInjectionPercent())
                .faultErrorCode(mc.getFaultErrorCode())
                .createdBy(mc.getCreatedBy())
                .createdAt(mc.getCreatedAt())
                .updatedAt(mc.getUpdatedAt())
                .build();
    }
}
