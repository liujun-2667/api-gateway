package com.apigateway.admin.service;

import com.apigateway.admin.annotation.Auditable;
import com.apigateway.admin.entity.AdminUser;
import com.apigateway.admin.repository.RouteRuleApprovalRepository;
import com.apigateway.admin.repository.RouteRuleRepository;
import com.apigateway.admin.repository.RouteRuleVersionRepository;
import com.apigateway.admin.security.CustomUserDetailsService;
import com.apigateway.common.dto.RouteRuleApprovalDTO;
import com.apigateway.common.entity.RouteRule;
import com.apigateway.common.entity.RouteRuleApproval;
import com.apigateway.common.entity.RouteRuleVersion;
import com.apigateway.common.enums.ApprovalStatus;
import com.apigateway.common.enums.OperationType;
import com.apigateway.common.enums.RuleStatus;
import com.apigateway.common.exception.BusinessException;
import com.apigateway.common.exception.NotFoundException;
import com.apigateway.common.utils.ApiKeyGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final RouteRuleApprovalRepository approvalRepository;
    private final RouteRuleRepository routeRuleRepository;
    private final RouteRuleVersionRepository versionRepository;
    private final CustomUserDetailsService userDetailsService;

    @Transactional(readOnly = true)
    public List<RouteRuleApprovalDTO.RouteRuleApprovalResponse> getApprovalsByRuleId(Long ruleId) {
        return approvalRepository.findByRouteRuleIdOrderByCreatedAtDesc(ruleId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RouteRuleApprovalDTO.RouteRuleApprovalResponse> getPendingApprovals() {
        return approvalRepository.findByStatus(ApprovalStatus.PENDING).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Auditable(resourceType = "RouteRuleApproval", operationType = OperationType.CREATE)
    @Transactional
    public RouteRuleApprovalDTO.RouteRuleApprovalResponse requestApproval(RouteRuleApprovalDTO.RouteRuleApprovalRequest request) {
        RouteRule rule = routeRuleRepository.findById(request.getRouteRuleId())
                .orElseThrow(() -> new NotFoundException("RouteRule", request.getRouteRuleId().toString()));

        RouteRuleVersion version = versionRepository.findById(request.getRouteRuleVersionId())
                .orElseThrow(() -> new NotFoundException("RouteRuleVersion", request.getRouteRuleVersionId().toString()));

        AdminUser requester = getCurrentUser();

        RouteRuleApproval approval = RouteRuleApproval.builder()
                .approvalId(ApiKeyGenerator.generateKeyId())
                .routeRule(rule)
                .routeRuleVersion(version)
                .status(ApprovalStatus.PENDING)
                .requesterId(requester != null ? requester.getId().toString() : null)
                .requesterName(requester != null ? requester.getUsername() : null)
                .requestComment(request.getRequestComment())
                .build();

        rule.setStatus(RuleStatus.PENDING_APPROVAL);
        routeRuleRepository.save(rule);

        approval = approvalRepository.save(approval);
        return toResponse(approval);
    }

    @Auditable(resourceType = "RouteRuleApproval", operationType = OperationType.APPROVE)
    @Transactional
    public RouteRuleApprovalDTO.RouteRuleApprovalResponse approveApproval(Long approvalId, RouteRuleApprovalDTO.ApprovalActionRequest request) {
        RouteRuleApproval approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new NotFoundException("RouteRuleApproval", approvalId.toString()));

        if (approval.getStatus() != ApprovalStatus.PENDING) {
            throw new BusinessException("Approval is not in PENDING status");
        }

        AdminUser approver = getCurrentUser();

        approval.setStatus(request.getStatus());
        approval.setApproverId(approver != null ? approver.getId().toString() : null);
        approval.setApproverName(approver != null ? approver.getUsername() : null);
        approval.setApprovalComment(request.getApprovalComment());
        approval.setApprovedAt(LocalDateTime.now());

        if (request.getStatus() == ApprovalStatus.APPROVED) {
            RouteRule rule = approval.getRouteRule();
            rule.setStatus(RuleStatus.ACTIVE);
            routeRuleRepository.save(rule);
        } else if (request.getStatus() == ApprovalStatus.REJECTED) {
            RouteRule rule = approval.getRouteRule();
            rule.setStatus(RuleStatus.DRAFT);
            routeRuleRepository.save(rule);
        }

        approval = approvalRepository.save(approval);
        return toResponse(approval);
    }

    @Auditable(resourceType = "RouteRuleApproval", operationType = OperationType.UPDATE)
    @Transactional
    public RouteRuleApprovalDTO.RouteRuleApprovalResponse emergencyPublish(Long ruleId, String comment) {
        RouteRule rule = routeRuleRepository.findById(ruleId)
                .orElseThrow(() -> new NotFoundException("RouteRule", ruleId.toString()));

        RouteRuleVersion latestVersion = versionRepository.findFirstByRouteRuleIdOrderByVersionDesc(ruleId)
                .orElseThrow(() -> new BusinessException("No version found for rule"));

        AdminUser approver = getCurrentUser();

        RouteRuleApproval approval = RouteRuleApproval.builder()
                .approvalId(ApiKeyGenerator.generateKeyId())
                .routeRule(rule)
                .routeRuleVersion(latestVersion)
                .status(ApprovalStatus.APPROVED)
                .approverId(approver != null ? approver.getId().toString() : null)
                .approverName(approver != null ? approver.getUsername() : null)
                .approvalComment("EMERGENCY PUBLISH: " + (comment != null ? comment : ""))
                .requesterId(approver != null ? approver.getId().toString() : null)
                .requesterName(approver != null ? approver.getUsername() : null)
                .requestComment("Emergency publish request")
                .approvedAt(LocalDateTime.now())
                .build();

        rule.setStatus(RuleStatus.ACTIVE);
        routeRuleRepository.save(rule);

        approval = approvalRepository.save(approval);
        return toResponse(approval);
    }

    private AdminUser getCurrentUser() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()
                    && !"anonymousUser".equals(authentication.getPrincipal())) {
                String username = authentication.getName();
                return userDetailsService.loadAdminUserByUsername(username);
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private RouteRuleApprovalDTO.RouteRuleApprovalResponse toResponse(RouteRuleApproval approval) {
        return RouteRuleApprovalDTO.RouteRuleApprovalResponse.builder()
                .id(approval.getId())
                .approvalId(approval.getApprovalId())
                .routeRuleId(approval.getRouteRule() != null ? approval.getRouteRule().getId() : null)
                .routeRuleName(approval.getRouteRule() != null ? approval.getRouteRule().getName() : null)
                .routeRuleVersionId(approval.getRouteRuleVersion() != null ? approval.getRouteRuleVersion().getId() : null)
                .routeRuleVersion(approval.getRouteRuleVersion() != null ? approval.getRouteRuleVersion().getVersion() : null)
                .status(approval.getStatus())
                .approverId(approval.getApproverId())
                .approverName(approval.getApproverName())
                .approvalComment(approval.getApprovalComment())
                .approvedAt(approval.getApprovedAt())
                .requesterId(approval.getRequesterId())
                .requesterName(approval.getRequesterName())
                .requestComment(approval.getRequestComment())
                .createdAt(approval.getCreatedAt())
                .updatedAt(approval.getUpdatedAt())
                .build();
    }
}
