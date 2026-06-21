package com.apigateway.admin.service;

import com.apigateway.admin.annotation.Auditable;
import com.apigateway.admin.repository.ApplicationRepository;
import com.apigateway.admin.repository.RouteRuleRepository;
import com.apigateway.admin.repository.RouteRuleVersionRepository;
import com.apigateway.admin.repository.TenantRepository;
import com.apigateway.common.dto.PageResponse;
import com.apigateway.common.dto.RouteRuleDTO;
import com.apigateway.common.dto.RouteRuleVersionDTO;
import com.apigateway.common.entity.Application;
import com.apigateway.common.entity.RouteRule;
import com.apigateway.common.entity.RouteRuleVersion;
import com.apigateway.common.entity.Tenant;
import com.apigateway.common.enums.OperationType;
import com.apigateway.common.enums.RuleStatus;
import com.apigateway.common.exception.BusinessException;
import com.apigateway.common.exception.NotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RouteRuleService {

    private final RouteRuleRepository routeRuleRepository;
    private final RouteRuleVersionRepository routeRuleVersionRepository;
    private final TenantRepository tenantRepository;
    private final ApplicationRepository applicationRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PageResponse<RouteRuleDTO.RouteRuleResponse> getRouteRulesByAppId(Long appId, Pageable pageable) {
        if (!applicationRepository.existsById(appId)) {
            throw new NotFoundException("Application", appId.toString());
        }
        List<RouteRule> rules = routeRuleRepository.findByApplicationId(appId);
        return PageResponse.<RouteRuleDTO.RouteRuleResponse>builder()
                .content(rules.stream().map(this::toResponse).collect(Collectors.toList()))
                .pageNumber(0)
                .pageSize(rules.size())
                .totalElements(rules.size())
                .totalPages(1)
                .first(true)
                .last(true)
                .build();
    }

    @Transactional(readOnly = true)
    public RouteRuleDTO.RouteRuleResponse getRouteRuleById(Long id) {
        RouteRule rule = routeRuleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("RouteRule", id.toString()));
        return toResponse(rule);
    }

    @Transactional(readOnly = true)
    public List<RouteRuleVersionDTO.RouteRuleVersionResponse> getRouteRuleVersions(Long ruleId) {
        return routeRuleVersionRepository.findByRouteRuleIdOrderByVersionDesc(ruleId).stream()
                .map(this::toVersionResponse)
                .collect(Collectors.toList());
    }

    @Auditable(resourceType = "RouteRule", operationType = OperationType.CREATE)
    @Transactional
    public RouteRuleDTO.RouteRuleResponse createRouteRule(Long appId, RouteRuleDTO.RouteRuleRequest request, String createdBy) {
        if (routeRuleRepository.existsByRuleId(request.getRuleId())) {
            throw new BusinessException("Rule ID already exists");
        }

        Tenant tenant = tenantRepository.findById(request.getTenantId())
                .orElseThrow(() -> new NotFoundException("Tenant", request.getTenantId().toString()));

        Application application = applicationRepository.findById(appId)
                .orElseThrow(() -> new NotFoundException("Application", appId.toString()));

        RuleStatus status = request.getStatus() != null ? request.getStatus() : RuleStatus.DRAFT;

        RouteRule rule = RouteRule.builder()
                .ruleId(request.getRuleId())
                .name(request.getName())
                .description(request.getDescription())
                .tenant(tenant)
                .application(application)
                .path(request.getPath())
                .method(request.getMethod())
                .targetUrl(request.getTargetUrl())
                .priority(request.getPriority())
                .status(status)
                .requiresAuth(request.getRequiresAuth() != null ? request.getRequiresAuth() : true)
                .rateLimitEnabled(request.getRateLimitEnabled() != null ? request.getRateLimitEnabled() : false)
                .circuitBreakerEnabled(request.getCircuitBreakerEnabled() != null ? request.getCircuitBreakerEnabled() : false)
                .effectiveFrom(request.getEffectiveFrom())
                .effectiveTo(request.getEffectiveTo())
                .createdBy(createdBy)
                .updatedBy(createdBy)
                .build();

        rule = routeRuleRepository.save(rule);
        createVersion(rule, 1, "Initial version", createdBy);
        return toResponse(rule);
    }

    @Auditable(resourceType = "RouteRule", operationType = OperationType.UPDATE)
    @Transactional
    public RouteRuleDTO.RouteRuleResponse updateRouteRule(Long id, RouteRuleDTO.RouteRuleRequest request, String updatedBy, String changeLog) {
        RouteRule rule = routeRuleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("RouteRule", id.toString()));

        if (rule.getStatus() == RuleStatus.ACTIVE) {
            throw new BusinessException("Cannot update active rule, create a new version first");
        }

        RouteRuleVersion latestVersion = routeRuleVersionRepository
                .findFirstByRouteRuleIdOrderByVersionDesc(id)
                .orElse(null);
        int newVersionNumber = latestVersion != null ? latestVersion.getVersion() + 1 : 1;

        rule.setName(request.getName());
        rule.setDescription(request.getDescription());
        rule.setPath(request.getPath());
        rule.setMethod(request.getMethod());
        rule.setTargetUrl(request.getTargetUrl());
        rule.setPriority(request.getPriority());
        if (request.getStatus() != null) {
            rule.setStatus(request.getStatus());
        }
        rule.setRequiresAuth(request.getRequiresAuth() != null ? request.getRequiresAuth() : rule.getRequiresAuth());
        rule.setRateLimitEnabled(request.getRateLimitEnabled() != null ? request.getRateLimitEnabled() : rule.getRateLimitEnabled());
        rule.setCircuitBreakerEnabled(request.getCircuitBreakerEnabled() != null ? request.getCircuitBreakerEnabled() : rule.getCircuitBreakerEnabled());
        rule.setEffectiveFrom(request.getEffectiveFrom());
        rule.setEffectiveTo(request.getEffectiveTo());
        rule.setUpdatedBy(updatedBy);

        rule = routeRuleRepository.save(rule);
        createVersion(rule, newVersionNumber, changeLog != null ? changeLog : "Updated", updatedBy);
        return toResponse(rule);
    }

    @Auditable(resourceType = "RouteRule", operationType = OperationType.DELETE)
    @Transactional
    public void deleteRouteRule(Long id) {
        if (!routeRuleRepository.existsById(id)) {
            throw new NotFoundException("RouteRule", id.toString());
        }
        routeRuleRepository.deleteById(id);
    }

    @Auditable(resourceType = "RouteRule", operationType = OperationType.UPDATE)
    @Transactional
    public RouteRuleDTO.RouteRuleResponse publishRouteRule(Long id) {
        RouteRule rule = routeRuleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("RouteRule", id.toString()));
        rule.setStatus(RuleStatus.ACTIVE);
        rule = routeRuleRepository.save(rule);
        return toResponse(rule);
    }

    @Auditable(resourceType = "RouteRule", operationType = OperationType.UPDATE)
    @Transactional
    public RouteRuleDTO.RouteRuleResponse rollbackRouteRule(Long id, Integer versionNumber) {
        RouteRule rule = routeRuleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("RouteRule", id.toString()));

        RouteRuleVersion version = routeRuleVersionRepository.findByRouteRuleIdAndVersion(id, versionNumber)
                .orElseThrow(() -> new NotFoundException("RouteRuleVersion", versionNumber.toString()));

        rule.setName(version.getName());
        rule.setDescription(version.getDescription());
        rule.setPath(version.getPath());
        rule.setMethod(version.getMethod());
        rule.setTargetUrl(version.getTargetUrl());
        rule.setPriority(version.getPriority());
        rule.setStatus(RuleStatus.DRAFT);
        rule.setRequiresAuth(version.getRequiresAuth());
        rule.setRateLimitEnabled(version.getRateLimitEnabled());
        rule.setCircuitBreakerEnabled(version.getCircuitBreakerEnabled());

        rule = routeRuleRepository.save(rule);

        RouteRuleVersion latestVersion = routeRuleVersionRepository
                .findFirstByRouteRuleIdOrderByVersionDesc(id)
                .orElse(null);
        int newVersionNumber = latestVersion != null ? latestVersion.getVersion() + 1 : 1;
        createVersion(rule, newVersionNumber, "Rolled back to version " + versionNumber, rule.getUpdatedBy());

        return toResponse(rule);
    }

    private void createVersion(RouteRule rule, int versionNumber, String changeLog, String createdBy) {
        String configSnapshot;
        try {
            configSnapshot = objectMapper.writeValueAsString(rule);
        } catch (JsonProcessingException e) {
            configSnapshot = "{}";
        }

        RouteRuleVersion version = RouteRuleVersion.builder()
                .versionId("v_" + rule.getRuleId() + "_" + versionNumber)
                .routeRule(rule)
                .version(versionNumber)
                .name(rule.getName())
                .description(rule.getDescription())
                .path(rule.getPath())
                .method(rule.getMethod())
                .targetUrl(rule.getTargetUrl())
                .priority(rule.getPriority())
                .status(rule.getStatus())
                .requiresAuth(rule.getRequiresAuth())
                .rateLimitEnabled(rule.getRateLimitEnabled())
                .circuitBreakerEnabled(rule.getCircuitBreakerEnabled())
                .configSnapshot(configSnapshot)
                .changeLog(changeLog)
                .createdBy(createdBy)
                .build();

        routeRuleVersionRepository.save(version);
    }

    private RouteRuleDTO.RouteRuleResponse toResponse(RouteRule rule) {
        return RouteRuleDTO.RouteRuleResponse.builder()
                .id(rule.getId())
                .ruleId(rule.getRuleId())
                .name(rule.getName())
                .description(rule.getDescription())
                .tenantId(rule.getTenant() != null ? rule.getTenant().getId() : null)
                .tenantName(rule.getTenant() != null ? rule.getTenant().getName() : null)
                .applicationId(rule.getApplication() != null ? rule.getApplication().getId() : null)
                .applicationName(rule.getApplication() != null ? rule.getApplication().getName() : null)
                .path(rule.getPath())
                .method(rule.getMethod())
                .targetUrl(rule.getTargetUrl())
                .priority(rule.getPriority())
                .status(rule.getStatus())
                .requiresAuth(rule.getRequiresAuth())
                .rateLimitEnabled(rule.getRateLimitEnabled())
                .circuitBreakerEnabled(rule.getCircuitBreakerEnabled())
                .effectiveFrom(rule.getEffectiveFrom())
                .effectiveTo(rule.getEffectiveTo())
                .createdBy(rule.getCreatedBy())
                .updatedBy(rule.getUpdatedBy())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }

    private RouteRuleVersionDTO.RouteRuleVersionResponse toVersionResponse(RouteRuleVersion version) {
        return RouteRuleVersionDTO.RouteRuleVersionResponse.builder()
                .id(version.getId())
                .versionId(version.getVersionId())
                .routeRuleId(version.getRouteRule() != null ? version.getRouteRule().getId() : null)
                .version(version.getVersion())
                .name(version.getName())
                .description(version.getDescription())
                .path(version.getPath())
                .method(version.getMethod())
                .targetUrl(version.getTargetUrl())
                .priority(version.getPriority())
                .status(version.getStatus())
                .requiresAuth(version.getRequiresAuth())
                .rateLimitEnabled(version.getRateLimitEnabled())
                .circuitBreakerEnabled(version.getCircuitBreakerEnabled())
                .configSnapshot(version.getConfigSnapshot())
                .changeLog(version.getChangeLog())
                .createdBy(version.getCreatedBy())
                .createdAt(version.getCreatedAt())
                .build();
    }
}
