package com.apigateway.admin.service;

import com.apigateway.admin.entity.AdminUser;
import com.apigateway.admin.repository.AuditLogRepository;
import com.apigateway.admin.security.CustomUserDetailsService;
import com.apigateway.common.dto.AuditLogDTO;
import com.apigateway.common.dto.PageResponse;
import com.apigateway.common.entity.AuditLog;
import com.apigateway.common.entity.Tenant;
import com.apigateway.common.enums.OperationType;
import com.apigateway.common.utils.ApiKeyGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final CustomUserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;

    private static final int RETENTION_DAYS = 90;

    @Transactional(readOnly = true)
    public PageResponse<AuditLogDTO.AuditLogResponse> getAuditLogs(Long tenantId, String resourceType, Pageable pageable) {
        Page<AuditLog> page;
        if (tenantId != null && resourceType != null) {
            page = auditLogRepository.findByTenantIdAndResourceType(tenantId, resourceType, pageable);
        } else if (tenantId != null) {
            page = auditLogRepository.findByTenantId(tenantId, pageable);
        } else if (resourceType != null) {
            page = auditLogRepository.findByResourceType(resourceType, pageable);
        } else {
            page = auditLogRepository.findAll(pageable);
        }
        return toPageResponse(page);
    }

    @Transactional
    public void log(String resourceType, String resourceId, OperationType operationType,
                    Object beforeValue, Object afterValue, boolean success, String errorMessage,
                    Tenant tenant) {
        String operatorId = null;
        String operatorName = null;
        String operatorIp = null;
        String requestUri = null;
        String requestMethod = null;

        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()
                    && !"anonymousUser".equals(authentication.getPrincipal())) {
                String username = authentication.getName();
                AdminUser user = userDetailsService.loadAdminUserByUsername(username);
                operatorId = user.getId().toString();
                operatorName = user.getUsername();
            }

            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                operatorIp = getClientIp(request);
                requestUri = request.getRequestURI();
                requestMethod = request.getMethod();
            }
        } catch (Exception e) {
            log.warn("Failed to collect audit log context", e);
        }

        String beforeJson = serializeObject(beforeValue);
        String afterJson = serializeObject(afterValue);

        AuditLog auditLog = AuditLog.builder()
                .logId(ApiKeyGenerator.generateKeyId())
                .tenant(tenant)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .operationType(operationType)
                .operatorId(operatorId)
                .operatorName(operatorName)
                .operatorIp(operatorIp)
                .beforeValue(beforeJson)
                .afterValue(afterJson)
                .success(success)
                .errorMessage(errorMessage)
                .requestUri(requestUri)
                .requestMethod(requestMethod)
                .build();

        auditLogRepository.save(auditLog);
    }

    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupOldLogs() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(RETENTION_DAYS);
        int deleted = auditLogRepository.deleteOlderThan(cutoffDate);
        log.info("Cleaned up {} audit logs older than {} days", deleted, RETENTION_DAYS);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String serializeObject(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return obj.toString();
        }
    }

    private AuditLogDTO.AuditLogResponse toResponse(AuditLog auditLog) {
        return AuditLogDTO.AuditLogResponse.builder()
                .id(auditLog.getId())
                .logId(auditLog.getLogId())
                .tenantId(auditLog.getTenant() != null ? auditLog.getTenant().getId() : null)
                .tenantName(auditLog.getTenant() != null ? auditLog.getTenant().getName() : null)
                .resourceType(auditLog.getResourceType())
                .resourceId(auditLog.getResourceId())
                .operationType(auditLog.getOperationType())
                .operatorId(auditLog.getOperatorId())
                .operatorName(auditLog.getOperatorName())
                .operatorIp(auditLog.getOperatorIp())
                .beforeValue(auditLog.getBeforeValue())
                .afterValue(auditLog.getAfterValue())
                .success(auditLog.getSuccess())
                .errorMessage(auditLog.getErrorMessage())
                .requestUri(auditLog.getRequestUri())
                .requestMethod(auditLog.getRequestMethod())
                .createdAt(auditLog.getCreatedAt())
                .build();
    }

    private PageResponse<AuditLogDTO.AuditLogResponse> toPageResponse(Page<AuditLog> page) {
        List<AuditLogDTO.AuditLogResponse> content = page.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return PageResponse.<AuditLogDTO.AuditLogResponse>builder()
                .content(content)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }
}
