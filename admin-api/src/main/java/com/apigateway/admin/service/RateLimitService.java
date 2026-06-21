package com.apigateway.admin.service;

import com.apigateway.admin.annotation.Auditable;
import com.apigateway.admin.repository.ApiKeyRepository;
import com.apigateway.admin.repository.RateLimitConfigRepository;
import com.apigateway.admin.repository.RouteRuleRepository;
import com.apigateway.admin.repository.TenantRepository;
import com.apigateway.common.dto.RateLimitConfigDTO;
import com.apigateway.common.entity.ApiKey;
import com.apigateway.common.entity.RateLimitConfig;
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
public class RateLimitService {

    private final RateLimitConfigRepository rateLimitConfigRepository;
    private final TenantRepository tenantRepository;
    private final RouteRuleRepository routeRuleRepository;
    private final ApiKeyRepository apiKeyRepository;

    @Transactional(readOnly = true)
    public List<RateLimitConfigDTO.RateLimitConfigResponse> getRateLimitsByAppId(Long appId) {
        if (!routeRuleRepository.existsById(appId)) {
            throw new NotFoundException("Application", appId.toString());
        }
        return rateLimitConfigRepository.findByTenantId(appId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RateLimitConfigDTO.RateLimitConfigResponse getRateLimitById(Long id) {
        RateLimitConfig config = rateLimitConfigRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("RateLimitConfig", id.toString()));
        return toResponse(config);
    }

    @Auditable(resourceType = "RateLimitConfig", operationType = OperationType.CREATE)
    @Transactional
    public RateLimitConfigDTO.RateLimitConfigResponse createRateLimit(Long appId, RateLimitConfigDTO.RateLimitConfigRequest request) {
        if (rateLimitConfigRepository.existsByConfigId(request.getConfigId())) {
            throw new BusinessException("Config ID already exists");
        }

        Tenant tenant = tenantRepository.findById(request.getTenantId())
                .orElseThrow(() -> new NotFoundException("Tenant", request.getTenantId().toString()));

        RouteRule routeRule = null;
        if (request.getRouteRuleId() != null) {
            routeRule = routeRuleRepository.findById(request.getRouteRuleId())
                    .orElseThrow(() -> new NotFoundException("RouteRule", request.getRouteRuleId().toString()));
        }

        ApiKey apiKey = null;
        if (request.getApiKeyId() != null) {
            apiKey = apiKeyRepository.findById(request.getApiKeyId())
                    .orElseThrow(() -> new NotFoundException("ApiKey", request.getApiKeyId().toString()));
        }

        RateLimitConfig config = RateLimitConfig.builder()
                .configId(request.getConfigId())
                .name(request.getName())
                .description(request.getDescription())
                .tenant(tenant)
                .routeRule(routeRule)
                .apiKey(apiKey)
                .requestsPerSecond(request.getRequestsPerSecond())
                .requestsPerMinute(request.getRequestsPerMinute())
                .requestsPerHour(request.getRequestsPerHour())
                .requestsPerDay(request.getRequestsPerDay())
                .burstCapacity(request.getBurstCapacity())
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .scope(request.getScope())
                .build();

        config = rateLimitConfigRepository.save(config);
        return toResponse(config);
    }

    @Auditable(resourceType = "RateLimitConfig", operationType = OperationType.UPDATE)
    @Transactional
    public RateLimitConfigDTO.RateLimitConfigResponse updateRateLimit(Long id, RateLimitConfigDTO.RateLimitConfigRequest request) {
        RateLimitConfig config = rateLimitConfigRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("RateLimitConfig", id.toString()));

        config.setName(request.getName());
        config.setDescription(request.getDescription());
        config.setRequestsPerSecond(request.getRequestsPerSecond());
        config.setRequestsPerMinute(request.getRequestsPerMinute());
        config.setRequestsPerHour(request.getRequestsPerHour());
        config.setRequestsPerDay(request.getRequestsPerDay());
        config.setBurstCapacity(request.getBurstCapacity());
        if (request.getEnabled() != null) {
            config.setEnabled(request.getEnabled());
        }
        config.setScope(request.getScope());

        config = rateLimitConfigRepository.save(config);
        return toResponse(config);
    }

    @Auditable(resourceType = "RateLimitConfig", operationType = OperationType.DELETE)
    @Transactional
    public void deleteRateLimit(Long id) {
        if (!rateLimitConfigRepository.existsById(id)) {
            throw new NotFoundException("RateLimitConfig", id.toString());
        }
        rateLimitConfigRepository.deleteById(id);
    }

    private RateLimitConfigDTO.RateLimitConfigResponse toResponse(RateLimitConfig config) {
        return RateLimitConfigDTO.RateLimitConfigResponse.builder()
                .id(config.getId())
                .configId(config.getConfigId())
                .name(config.getName())
                .description(config.getDescription())
                .tenantId(config.getTenant() != null ? config.getTenant().getId() : null)
                .tenantName(config.getTenant() != null ? config.getTenant().getName() : null)
                .routeRuleId(config.getRouteRule() != null ? config.getRouteRule().getId() : null)
                .routeRuleName(config.getRouteRule() != null ? config.getRouteRule().getName() : null)
                .apiKeyId(config.getApiKey() != null ? config.getApiKey().getId() : null)
                .apiKeyName(config.getApiKey() != null ? config.getApiKey().getName() : null)
                .requestsPerSecond(config.getRequestsPerSecond())
                .requestsPerMinute(config.getRequestsPerMinute())
                .requestsPerHour(config.getRequestsPerHour())
                .requestsPerDay(config.getRequestsPerDay())
                .burstCapacity(config.getBurstCapacity())
                .enabled(config.getEnabled())
                .scope(config.getScope())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }
}
