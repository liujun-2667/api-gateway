package com.apigateway.admin.service;

import com.apigateway.admin.annotation.Auditable;
import com.apigateway.admin.repository.ApplicationRepository;
import com.apigateway.admin.repository.RouteRuleRepository;
import com.apigateway.admin.repository.RouteRuleVersionRepository;
import com.apigateway.admin.repository.TenantRepository;
import com.apigateway.common.dto.BatchOperationDTO;
import com.apigateway.common.dto.PageResponse;
import com.apigateway.common.dto.RouteRuleDTO;
import com.apigateway.common.dto.RouteRuleVersionDTO;
import com.apigateway.common.dto.VersionDiffDTO;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RouteRuleService {

    private final RouteRuleRepository routeRuleRepository;

    @Autowired
    private RouteRuleVersionRepository routeRuleVersionRepository;

    private final TenantRepository tenantRepository;
    private final ApplicationRepository applicationRepository;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;

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
        return routeRuleVersionRepository.findTop10ByRouteRuleIdOrderByVersionDesc(ruleId).stream()
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

    @Transactional(readOnly = true)
    public VersionDiffDTO.DiffResponse compareVersions(Long ruleId, Long version1Id, Long version2Id) {
        RouteRuleVersion version1 = routeRuleVersionRepository.findById(version1Id)
                .orElseThrow(() -> new NotFoundException("RouteRuleVersion", version1Id.toString()));
        RouteRuleVersion version2 = routeRuleVersionRepository.findById(version2Id)
                .orElseThrow(() -> new NotFoundException("RouteRuleVersion", version2Id.toString()));

        if (!version1.getRouteRule().getId().equals(ruleId) || !version2.getRouteRule().getId().equals(ruleId)) {
            throw new BusinessException("Both versions must belong to the same route rule");
        }

        List<VersionDiffDTO.DiffField> diffs = new ArrayList<>();
        Map<String, List<VersionDiffDTO.DiffField>> diffsByCategory = new LinkedHashMap<>();
        diffsByCategory.put("targetBackends", new ArrayList<>());
        diffsByCategory.put("timeoutSettings", new ArrayList<>());
        diffsByCategory.put("retryStrategy", new ArrayList<>());
        diffsByCategory.put("requestRewrite", new ArrayList<>());
        diffsByCategory.put("other", new ArrayList<>());

        compareField(version1.getTargetBackends(), version2.getTargetBackends(), "targetBackends", "targetBackends", diffs, diffsByCategory);

        compareField(version1.getConnectTimeoutMs(), version2.getConnectTimeoutMs(), "connectTimeoutMs", "timeoutSettings", diffs, diffsByCategory);
        compareField(version1.getReadTimeoutMs(), version2.getReadTimeoutMs(), "readTimeoutMs", "timeoutSettings", diffs, diffsByCategory);

        compareField(version1.getMaxRetries(), version2.getMaxRetries(), "maxRetries", "retryStrategy", diffs, diffsByCategory);
        compareField(version1.getRetryOn5xx(), version2.getRetryOn5xx(), "retryOn5xx", "retryStrategy", diffs, diffsByCategory);
        compareField(version1.getRetryOnTimeout(), version2.getRetryOnTimeout(), "retryOnTimeout", "retryStrategy", diffs, diffsByCategory);
        compareField(version1.getRetryIntervalMs(), version2.getRetryIntervalMs(), "retryIntervalMs", "retryStrategy", diffs, diffsByCategory);

        compareField(version1.getRequestHeadersToAdd(), version2.getRequestHeadersToAdd(), "requestHeadersToAdd", "requestRewrite", diffs, diffsByCategory);
        compareField(version1.getRequestHeadersToRemove(), version2.getRequestHeadersToRemove(), "requestHeadersToRemove", "requestRewrite", diffs, diffsByCategory);
        compareField(version1.getPathPrefixReplacement(), version2.getPathPrefixReplacement(), "pathPrefixReplacement", "requestRewrite", diffs, diffsByCategory);

        compareField(version1.getName(), version2.getName(), "name", "other", diffs, diffsByCategory);
        compareField(version1.getDescription(), version2.getDescription(), "description", "other", diffs, diffsByCategory);
        compareField(version1.getPath(), version2.getPath(), "path", "other", diffs, diffsByCategory);
        compareField(version1.getMethod(), version2.getMethod(), "method", "other", diffs, diffsByCategory);
        compareField(version1.getTargetUrl(), version2.getTargetUrl(), "targetUrl", "other", diffs, diffsByCategory);
        compareField(version1.getPriority(), version2.getPriority(), "priority", "other", diffs, diffsByCategory);
        compareField(version1.getStatus(), version2.getStatus(), "status", "other", diffs, diffsByCategory);
        compareField(version1.getRequiresAuth(), version2.getRequiresAuth(), "requiresAuth", "other", diffs, diffsByCategory);
        compareField(version1.getRateLimitEnabled(), version2.getRateLimitEnabled(), "rateLimitEnabled", "other", diffs, diffsByCategory);
        compareField(version1.getCircuitBreakerEnabled(), version2.getCircuitBreakerEnabled(), "circuitBreakerEnabled", "other", diffs, diffsByCategory);

        return VersionDiffDTO.DiffResponse.builder()
                .version1Id(version1Id)
                .version2Id(version2Id)
                .diffs(diffs)
                .diffsByCategory(diffsByCategory)
                .build();
    }

    private void compareField(Object oldValue, Object newValue, String fieldName, String category,
                              List<VersionDiffDTO.DiffField> diffs,
                              Map<String, List<VersionDiffDTO.DiffField>> diffsByCategory) {
        if (Objects.equals(oldValue, newValue)) {
            return;
        }

        String changeType;
        if (oldValue == null && newValue != null) {
            changeType = "ADD";
        } else if (oldValue != null && newValue == null) {
            changeType = "REMOVE";
        } else {
            changeType = "MODIFY";
        }

        String oldValueStr = convertToString(oldValue);
        String newValueStr = convertToString(newValue);

        VersionDiffDTO.DiffField diffField = VersionDiffDTO.DiffField.builder()
                .fieldName(fieldName)
                .oldValue(oldValueStr)
                .newValue(newValueStr)
                .changeType(changeType)
                .build();

        diffs.add(diffField);
        diffsByCategory.get(category).add(diffField);
    }

    private String convertToString(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return value.toString();
        }
    }

    @Auditable(resourceType = "RouteRule", operationType = OperationType.UPDATE)
    @Transactional
    public RouteRuleDTO.RouteRuleResponse rollbackRouteRuleWithReason(Long id, Integer versionNumber, String reason, String operator) {
        RouteRule rule = routeRuleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("RouteRule", id.toString()));

        RouteRuleVersion version = routeRuleVersionRepository.findByRouteRuleIdAndVersion(id, versionNumber)
                .orElseThrow(() -> new NotFoundException("RouteRuleVersion", versionNumber.toString()));

        RouteRule beforeRollback = new RouteRule();
        beforeRollback.setName(rule.getName());
        beforeRollback.setDescription(rule.getDescription());
        beforeRollback.setPath(rule.getPath());
        beforeRollback.setMethod(rule.getMethod());
        beforeRollback.setTargetUrl(rule.getTargetUrl());
        beforeRollback.setPriority(rule.getPriority());
        beforeRollback.setStatus(rule.getStatus());
        beforeRollback.setRequiresAuth(rule.getRequiresAuth());
        beforeRollback.setRateLimitEnabled(rule.getRateLimitEnabled());
        beforeRollback.setCircuitBreakerEnabled(rule.getCircuitBreakerEnabled());
        beforeRollback.setTargetBackends(rule.getTargetBackends());
        beforeRollback.setConnectTimeoutMs(rule.getConnectTimeoutMs());
        beforeRollback.setReadTimeoutMs(rule.getReadTimeoutMs());
        beforeRollback.setMaxRetries(rule.getMaxRetries());
        beforeRollback.setRetryOn5xx(rule.getRetryOn5xx());
        beforeRollback.setRetryOnTimeout(rule.getRetryOnTimeout());
        beforeRollback.setRetryIntervalMs(rule.getRetryIntervalMs());
        beforeRollback.setRequestHeadersToAdd(rule.getRequestHeadersToAdd());
        beforeRollback.setRequestHeadersToRemove(rule.getRequestHeadersToRemove());
        beforeRollback.setPathPrefixReplacement(rule.getPathPrefixReplacement());

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
        rule.setTargetBackends(version.getTargetBackends());
        rule.setConnectTimeoutMs(version.getConnectTimeoutMs());
        rule.setReadTimeoutMs(version.getReadTimeoutMs());
        rule.setMaxRetries(version.getMaxRetries());
        rule.setRetryOn5xx(version.getRetryOn5xx());
        rule.setRetryOnTimeout(version.getRetryOnTimeout());
        rule.setRetryIntervalMs(version.getRetryIntervalMs());
        rule.setRequestHeadersToAdd(version.getRequestHeadersToAdd());
        rule.setRequestHeadersToRemove(version.getRequestHeadersToRemove());
        rule.setPathPrefixReplacement(version.getPathPrefixReplacement());
        rule.setUpdatedBy(operator);

        rule = routeRuleRepository.save(rule);

        RouteRuleVersion latestVersion = routeRuleVersionRepository
                .findFirstByRouteRuleIdOrderByVersionDesc(id)
                .orElse(null);
        int newVersionNumber = latestVersion != null ? latestVersion.getVersion() + 1 : 1;
        String changeLogMessage = "Rolled back to version " + versionNumber + ". Reason: " + reason;
        createVersion(rule, newVersionNumber, changeLogMessage, operator);

        auditLogService.log(
                "RouteRule",
                rule.getId().toString(),
                OperationType.UPDATE,
                beforeRollback,
                rule,
                true,
                null,
                rule.getTenant()
        );

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
                .targetBackends(rule.getTargetBackends())
                .connectTimeoutMs(rule.getConnectTimeoutMs())
                .readTimeoutMs(rule.getReadTimeoutMs())
                .maxRetries(rule.getMaxRetries())
                .retryOn5xx(rule.getRetryOn5xx())
                .retryOnTimeout(rule.getRetryOnTimeout())
                .retryIntervalMs(rule.getRetryIntervalMs())
                .requestHeadersToAdd(rule.getRequestHeadersToAdd())
                .requestHeadersToRemove(rule.getRequestHeadersToRemove())
                .pathPrefixReplacement(rule.getPathPrefixReplacement())
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
                .targetBackends(rule.getTargetBackends())
                .connectTimeoutMs(rule.getConnectTimeoutMs())
                .readTimeoutMs(rule.getReadTimeoutMs())
                .maxRetries(rule.getMaxRetries())
                .retryOn5xx(rule.getRetryOn5xx())
                .retryOnTimeout(rule.getRetryOnTimeout())
                .retryIntervalMs(rule.getRetryIntervalMs())
                .requestHeadersToAdd(rule.getRequestHeadersToAdd())
                .requestHeadersToRemove(rule.getRequestHeadersToRemove())
                .pathPrefixReplacement(rule.getPathPrefixReplacement())
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
                .targetBackends(version.getTargetBackends())
                .connectTimeoutMs(version.getConnectTimeoutMs())
                .readTimeoutMs(version.getReadTimeoutMs())
                .maxRetries(version.getMaxRetries())
                .retryOn5xx(version.getRetryOn5xx())
                .retryOnTimeout(version.getRetryOnTimeout())
                .retryIntervalMs(version.getRetryIntervalMs())
                .requestHeadersToAdd(version.getRequestHeadersToAdd())
                .requestHeadersToRemove(version.getRequestHeadersToRemove())
                .pathPrefixReplacement(version.getPathPrefixReplacement())
                .configSnapshot(version.getConfigSnapshot())
                .changeLog(version.getChangeLog())
                .createdBy(version.getCreatedBy())
                .createdAt(version.getCreatedAt())
                .build();
    }

    @Transactional
    public BatchOperationDTO.BatchOperationResponse batchOperation(Long appId, BatchOperationDTO.BatchOperationRequest request, String operator) {
        if (!applicationRepository.existsById(appId)) {
            throw new NotFoundException("Application", appId.toString());
        }

        String operation = request.getOperation().toUpperCase();
        List<BatchOperationDTO.BatchOperationResult> results = new ArrayList<>();
        int successCount = 0;
        int failedCount = 0;

        for (Long id : request.getIds()) {
            try {
                RouteRule rule = routeRuleRepository.findById(id).orElse(null);
                if (rule == null) {
                    results.add(BatchOperationDTO.BatchOperationResult.builder()
                            .id(id)
                            .success(false)
                            .message("Rule not found")
                            .build());
                    failedCount++;
                    continue;
                }

                if (!rule.getApplication().getId().equals(appId)) {
                    results.add(BatchOperationDTO.BatchOperationResult.builder()
                            .id(id)
                            .success(false)
                            .message("Rule does not belong to the specified application")
                            .build());
                    failedCount++;
                    continue;
                }

                OperationType auditOperationType;
                RouteRule beforeRule = cloneRule(rule);

                switch (operation) {
                    case "ENABLE":
                        auditOperationType = OperationType.BATCH_ENABLE;
                        if (rule.getStatus() == RuleStatus.ACTIVE) {
                            results.add(BatchOperationDTO.BatchOperationResult.builder()
                                    .id(id)
                                    .success(true)
                                    .message("Rule is already active")
                                    .build());
                            successCount++;
                            continue;
                        }
                        rule.setStatus(RuleStatus.ACTIVE);
                        rule.setUpdatedBy(operator);
                        routeRuleRepository.save(rule);
                        auditLogService.log("RouteRule", rule.getId().toString(), auditOperationType, beforeRule, rule, true, null, rule.getTenant());
                        break;

                    case "DISABLE":
                        auditOperationType = OperationType.BATCH_DISABLE;
                        if (rule.getStatus() == RuleStatus.INACTIVE) {
                            results.add(BatchOperationDTO.BatchOperationResult.builder()
                                    .id(id)
                                    .success(true)
                                    .message("Rule is already inactive")
                                    .build());
                            successCount++;
                            continue;
                        }
                        if (rule.getStatus() == RuleStatus.ACTIVE || rule.getStatus() == RuleStatus.DRAFT || rule.getStatus() == RuleStatus.PENDING_APPROVAL) {
                            rule.setStatus(RuleStatus.INACTIVE);
                            rule.setUpdatedBy(operator);
                            routeRuleRepository.save(rule);
                            auditLogService.log("RouteRule", rule.getId().toString(), auditOperationType, beforeRule, rule, true, null, rule.getTenant());
                        } else {
                            results.add(BatchOperationDTO.BatchOperationResult.builder()
                                    .id(id)
                                    .success(false)
                                    .message("Cannot disable rule with status: " + rule.getStatus())
                                    .build());
                            failedCount++;
                            continue;
                        }
                        break;

                    case "SUBMIT_APPROVAL":
                        auditOperationType = OperationType.BATCH_SUBMIT_APPROVAL;
                        if (rule.getStatus() == RuleStatus.PENDING_APPROVAL) {
                            results.add(BatchOperationDTO.BatchOperationResult.builder()
                                    .id(id)
                                    .success(true)
                                    .message("Rule is already pending approval")
                                    .build());
                            successCount++;
                            continue;
                        }
                        if (rule.getStatus() == RuleStatus.DRAFT || rule.getStatus() == RuleStatus.INACTIVE) {
                            rule.setStatus(RuleStatus.PENDING_APPROVAL);
                            rule.setUpdatedBy(operator);
                            routeRuleRepository.save(rule);
                            auditLogService.log("RouteRule", rule.getId().toString(), auditOperationType, beforeRule, rule, true, null, rule.getTenant());
                        } else {
                            results.add(BatchOperationDTO.BatchOperationResult.builder()
                                    .id(id)
                                    .success(false)
                                    .message("Cannot submit approval for rule with status: " + rule.getStatus())
                                    .build());
                            failedCount++;
                            continue;
                        }
                        break;

                    case "DELETE":
                        auditOperationType = OperationType.BATCH_DELETE;
                        if (rule.getStatus() == RuleStatus.ACTIVE) {
                            results.add(BatchOperationDTO.BatchOperationResult.builder()
                                    .id(id)
                                    .success(false)
                                    .message("Cannot delete active rule")
                                    .build());
                            failedCount++;
                            continue;
                        }
                        routeRuleRepository.delete(rule);
                        auditLogService.log("RouteRule", rule.getId().toString(), auditOperationType, beforeRule, null, true, null, rule.getTenant());
                        break;

                    default:
                        results.add(BatchOperationDTO.BatchOperationResult.builder()
                                .id(id)
                                .success(false)
                                .message("Unsupported operation: " + operation)
                                .build());
                        failedCount++;
                        continue;
                }

                results.add(BatchOperationDTO.BatchOperationResult.builder()
                        .id(id)
                        .success(true)
                        .message("Operation successful")
                        .build());
                successCount++;

            } catch (Exception e) {
                results.add(BatchOperationDTO.BatchOperationResult.builder()
                        .id(id)
                        .success(false)
                        .message("Error: " + e.getMessage())
                        .build());
                failedCount++;
            }
        }

        return BatchOperationDTO.BatchOperationResponse.builder()
                .results(results)
                .successCount(successCount)
                .failedCount(failedCount)
                .build();
    }

    private RouteRule cloneRule(RouteRule rule) {
        RouteRule clone = new RouteRule();
        clone.setId(rule.getId());
        clone.setRuleId(rule.getRuleId());
        clone.setName(rule.getName());
        clone.setDescription(rule.getDescription());
        clone.setPath(rule.getPath());
        clone.setMethod(rule.getMethod());
        clone.setTargetUrl(rule.getTargetUrl());
        clone.setPriority(rule.getPriority());
        clone.setStatus(rule.getStatus());
        clone.setRequiresAuth(rule.getRequiresAuth());
        clone.setRateLimitEnabled(rule.getRateLimitEnabled());
        clone.setCircuitBreakerEnabled(rule.getCircuitBreakerEnabled());
        clone.setEffectiveFrom(rule.getEffectiveFrom());
        clone.setEffectiveTo(rule.getEffectiveTo());
        clone.setCreatedBy(rule.getCreatedBy());
        clone.setUpdatedBy(rule.getUpdatedBy());
        clone.setTargetBackends(rule.getTargetBackends());
        clone.setConnectTimeoutMs(rule.getConnectTimeoutMs());
        clone.setReadTimeoutMs(rule.getReadTimeoutMs());
        clone.setMaxRetries(rule.getMaxRetries());
        clone.setRetryOn5xx(rule.getRetryOn5xx());
        clone.setRetryOnTimeout(rule.getRetryOnTimeout());
        clone.setRetryIntervalMs(rule.getRetryIntervalMs());
        clone.setRequestHeadersToAdd(rule.getRequestHeadersToAdd());
        clone.setRequestHeadersToRemove(rule.getRequestHeadersToRemove());
        clone.setPathPrefixReplacement(rule.getPathPrefixReplacement());
        return clone;
    }
}
