package com.apigateway.admin.service;

import com.apigateway.admin.annotation.Auditable;
import com.apigateway.admin.repository.CircuitBreakerConfigRepository;
import com.apigateway.admin.repository.RouteRuleRepository;
import com.apigateway.admin.repository.TenantRepository;
import com.apigateway.common.dto.CircuitBreakerConfigDTO;
import com.apigateway.common.entity.CircuitBreakerConfig;
import com.apigateway.common.entity.RouteRule;
import com.apigateway.common.entity.Tenant;
import com.apigateway.common.enums.OperationType;
import com.apigateway.common.exception.BusinessException;
import com.apigateway.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CircuitBreakerConfigService {

    private final CircuitBreakerConfigRepository circuitBreakerConfigRepository;
    private final TenantRepository tenantRepository;
    private final RouteRuleRepository routeRuleRepository;

    @Transactional(readOnly = true)
    public List<CircuitBreakerConfigDTO.CircuitBreakerConfigResponse> getCircuitBreakersByAppId(Long appId) {
        if (!routeRuleRepository.existsById(appId)) {
            throw new NotFoundException("Application", appId.toString());
        }
        return circuitBreakerConfigRepository.findByTenantId(appId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CircuitBreakerConfigDTO.CircuitBreakerConfigResponse getCircuitBreakerById(Long id) {
        CircuitBreakerConfig config = circuitBreakerConfigRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("CircuitBreakerConfig", id.toString()));
        return toResponse(config);
    }

    @Auditable(resourceType = "CircuitBreakerConfig", operationType = OperationType.CREATE)
    @Transactional
    public CircuitBreakerConfigDTO.CircuitBreakerConfigResponse createCircuitBreaker(Long appId, CircuitBreakerConfigDTO.CircuitBreakerConfigRequest request) {
        if (circuitBreakerConfigRepository.existsByConfigId(request.getConfigId())) {
            throw new BusinessException("Config ID already exists");
        }

        Tenant tenant = tenantRepository.findById(request.getTenantId())
                .orElseThrow(() -> new NotFoundException("Tenant", request.getTenantId().toString()));

        RouteRule routeRule = null;
        if (request.getRouteRuleId() != null) {
            routeRule = routeRuleRepository.findById(request.getRouteRuleId())
                    .orElseThrow(() -> new NotFoundException("RouteRule", request.getRouteRuleId().toString()));
        }

        CircuitBreakerConfig config = CircuitBreakerConfig.builder()
                .configId(request.getConfigId())
                .name(request.getName())
                .description(request.getDescription())
                .tenant(tenant)
                .routeRule(routeRule)
                .failureRateThreshold(request.getFailureRateThreshold())
                .slowCallRateThreshold(request.getSlowCallRateThreshold())
                .slowCallDurationThreshold(request.getSlowCallDurationThreshold())
                .permittedNumberOfCallsInHalfOpenState(request.getPermittedNumberOfCallsInHalfOpenState())
                .slidingWindowSize(request.getSlidingWindowSize())
                .slidingWindowType(request.getSlidingWindowType())
                .minimumNumberOfCalls(request.getMinimumNumberOfCalls())
                .waitDurationInOpenState(request.getWaitDurationInOpenState())
                .fallbackUrl(request.getFallbackUrl())
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .build();

        config = circuitBreakerConfigRepository.save(config);
        return toResponse(config);
    }

    @Auditable(resourceType = "CircuitBreakerConfig", operationType = OperationType.UPDATE)
    @Transactional
    public CircuitBreakerConfigDTO.CircuitBreakerConfigResponse updateCircuitBreaker(Long id, CircuitBreakerConfigDTO.CircuitBreakerConfigRequest request) {
        CircuitBreakerConfig config = circuitBreakerConfigRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("CircuitBreakerConfig", id.toString()));

        config.setName(request.getName());
        config.setDescription(request.getDescription());
        config.setFailureRateThreshold(request.getFailureRateThreshold());
        config.setSlowCallRateThreshold(request.getSlowCallRateThreshold());
        config.setSlowCallDurationThreshold(request.getSlowCallDurationThreshold());
        config.setPermittedNumberOfCallsInHalfOpenState(request.getPermittedNumberOfCallsInHalfOpenState());
        config.setSlidingWindowSize(request.getSlidingWindowSize());
        config.setSlidingWindowType(request.getSlidingWindowType());
        config.setMinimumNumberOfCalls(request.getMinimumNumberOfCalls());
        config.setWaitDurationInOpenState(request.getWaitDurationInOpenState());
        config.setFallbackUrl(request.getFallbackUrl());
        if (request.getEnabled() != null) {
            config.setEnabled(request.getEnabled());
        }

        config = circuitBreakerConfigRepository.save(config);
        return toResponse(config);
    }

    @Auditable(resourceType = "CircuitBreakerConfig", operationType = OperationType.DELETE)
    @Transactional
    public void deleteCircuitBreaker(Long id) {
        if (!circuitBreakerConfigRepository.existsById(id)) {
            throw new NotFoundException("CircuitBreakerConfig", id.toString());
        }
        circuitBreakerConfigRepository.deleteById(id);
    }

    private CircuitBreakerConfigDTO.CircuitBreakerConfigResponse toResponse(CircuitBreakerConfig config) {
        return CircuitBreakerConfigDTO.CircuitBreakerConfigResponse.builder()
                .id(config.getId())
                .configId(config.getConfigId())
                .name(config.getName())
                .description(config.getDescription())
                .tenantId(config.getTenant() != null ? config.getTenant().getId() : null)
                .tenantName(config.getTenant() != null ? config.getTenant().getName() : null)
                .routeRuleId(config.getRouteRule() != null ? config.getRouteRule().getId() : null)
                .routeRuleName(config.getRouteRule() != null ? config.getRouteRule().getName() : null)
                .failureRateThreshold(config.getFailureRateThreshold())
                .slowCallRateThreshold(config.getSlowCallRateThreshold())
                .slowCallDurationThreshold(config.getSlowCallDurationThreshold())
                .permittedNumberOfCallsInHalfOpenState(config.getPermittedNumberOfCallsInHalfOpenState())
                .slidingWindowSize(config.getSlidingWindowSize())
                .slidingWindowType(config.getSlidingWindowType())
                .minimumNumberOfCalls(config.getMinimumNumberOfCalls())
                .waitDurationInOpenState(config.getWaitDurationInOpenState())
                .fallbackUrl(config.getFallbackUrl())
                .enabled(config.getEnabled())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }
}
