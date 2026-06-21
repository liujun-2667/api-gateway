package com.apigateway.admin.service;

import com.apigateway.admin.annotation.Auditable;
import com.apigateway.admin.repository.RouteRuleRepository;
import com.apigateway.admin.repository.TenantRepository;
import com.apigateway.admin.repository.TrafficColorRuleRepository;
import com.apigateway.common.dto.TrafficColorRuleDTO;
import com.apigateway.common.entity.RouteRule;
import com.apigateway.common.entity.Tenant;
import com.apigateway.common.entity.TrafficColorRule;
import com.apigateway.common.enums.OperationType;
import com.apigateway.common.enums.RuleStatus;
import com.apigateway.common.exception.BusinessException;
import com.apigateway.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrafficColorRuleService {

    private final TrafficColorRuleRepository trafficColorRuleRepository;
    private final TenantRepository tenantRepository;
    private final RouteRuleRepository routeRuleRepository;

    @Transactional(readOnly = true)
    public List<TrafficColorRuleDTO.TrafficColorRuleResponse> getTrafficColorRulesByAppId(Long appId) {
        if (!routeRuleRepository.existsById(appId)) {
            throw new NotFoundException("Application", appId.toString());
        }
        return trafficColorRuleRepository.findByRouteRuleId(appId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TrafficColorRuleDTO.TrafficColorRuleResponse getTrafficColorRuleById(Long id) {
        TrafficColorRule rule = trafficColorRuleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("TrafficColorRule", id.toString()));
        return toResponse(rule);
    }

    @Auditable(resourceType = "TrafficColorRule", operationType = OperationType.CREATE)
    @Transactional
    public TrafficColorRuleDTO.TrafficColorRuleResponse createTrafficColorRule(Long appId, TrafficColorRuleDTO.TrafficColorRuleRequest request) {
        Tenant tenant = tenantRepository.findById(request.getTenantId())
                .orElseThrow(() -> new NotFoundException("Tenant", request.getTenantId().toString()));

        RouteRule routeRule = null;
        if (request.getRouteRuleId() != null) {
            routeRule = routeRuleRepository.findById(request.getRouteRuleId())
                    .orElseThrow(() -> new NotFoundException("RouteRule", request.getRouteRuleId().toString()));
        }

        RuleStatus status = request.getStatus() != null ? request.getStatus() : RuleStatus.ACTIVE;

        TrafficColorRule rule = TrafficColorRule.builder()
                .ruleId(request.getRuleId())
                .name(request.getName())
                .description(request.getDescription())
                .tenant(tenant)
                .routeRule(routeRule)
                .conditionType(request.getConditionType())
                .conditionKey(request.getConditionKey())
                .matchType(request.getMatchType())
                .conditionValue(request.getConditionValue())
                .colorTag(request.getColorTag())
                .operation(request.getOperation())
                .priority(request.getPriority())
                .status(status)
                .effectiveFrom(request.getEffectiveFrom())
                .effectiveTo(request.getEffectiveTo())
                .build();

        rule = trafficColorRuleRepository.save(rule);
        return toResponse(rule);
    }

    @Auditable(resourceType = "TrafficColorRule", operationType = OperationType.UPDATE)
    @Transactional
    public TrafficColorRuleDTO.TrafficColorRuleResponse updateTrafficColorRule(Long id, TrafficColorRuleDTO.TrafficColorRuleRequest request) {
        TrafficColorRule rule = trafficColorRuleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("TrafficColorRule", id.toString()));

        if (request.getRouteRuleId() != null) {
            RouteRule routeRule = routeRuleRepository.findById(request.getRouteRuleId())
                    .orElseThrow(() -> new NotFoundException("RouteRule", request.getRouteRuleId().toString()));
            rule.setRouteRule(routeRule);
        }

        rule.setName(request.getName());
        rule.setDescription(request.getDescription());
        rule.setConditionType(request.getConditionType());
        rule.setConditionKey(request.getConditionKey());
        rule.setMatchType(request.getMatchType());
        rule.setConditionValue(request.getConditionValue());
        rule.setColorTag(request.getColorTag());
        rule.setOperation(request.getOperation());
        rule.setPriority(request.getPriority());
        if (request.getStatus() != null) {
            rule.setStatus(request.getStatus());
        }
        rule.setEffectiveFrom(request.getEffectiveFrom());
        rule.setEffectiveTo(request.getEffectiveTo());

        rule = trafficColorRuleRepository.save(rule);
        return toResponse(rule);
    }

    @Auditable(resourceType = "TrafficColorRule", operationType = OperationType.DELETE)
    @Transactional
    public void deleteTrafficColorRule(Long id) {
        if (!trafficColorRuleRepository.existsById(id)) {
            throw new NotFoundException("TrafficColorRule", id.toString());
        }
        trafficColorRuleRepository.deleteById(id);
    }

    @Auditable(resourceType = "TrafficColorRule", operationType = OperationType.ACTIVATE)
    @Transactional
    public int enableAllRules(Long tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            throw new NotFoundException("Tenant", tenantId.toString());
        }
        return trafficColorRuleRepository.updateStatusByTenantId(tenantId, RuleStatus.ACTIVE);
    }

    @Auditable(resourceType = "TrafficColorRule", operationType = OperationType.DEACTIVATE)
    @Transactional
    public int disableAllRules(Long tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            throw new NotFoundException("Tenant", tenantId.toString());
        }
        return trafficColorRuleRepository.updateStatusByTenantId(tenantId, RuleStatus.INACTIVE);
    }

    @Auditable(resourceType = "TrafficColorRule", operationType = OperationType.DELETE)
    @Transactional
    public int clearAllRules(Long tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            throw new NotFoundException("Tenant", tenantId.toString());
        }
        return trafficColorRuleRepository.deleteByTenantId(tenantId);
    }

    private TrafficColorRuleDTO.TrafficColorRuleResponse toResponse(TrafficColorRule rule) {
        return TrafficColorRuleDTO.TrafficColorRuleResponse.builder()
                .id(rule.getId())
                .ruleId(rule.getRuleId())
                .name(rule.getName())
                .description(rule.getDescription())
                .tenantId(rule.getTenant() != null ? rule.getTenant().getId() : null)
                .tenantName(rule.getTenant() != null ? rule.getTenant().getName() : null)
                .routeRuleId(rule.getRouteRule() != null ? rule.getRouteRule().getId() : null)
                .routeRuleName(rule.getRouteRule() != null ? rule.getRouteRule().getName() : null)
                .conditionType(rule.getConditionType())
                .conditionKey(rule.getConditionKey())
                .matchType(rule.getMatchType())
                .conditionValue(rule.getConditionValue())
                .colorTag(rule.getColorTag())
                .operation(rule.getOperation())
                .priority(rule.getPriority())
                .status(rule.getStatus())
                .effectiveFrom(rule.getEffectiveFrom())
                .effectiveTo(rule.getEffectiveTo())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }
}
