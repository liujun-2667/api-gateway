package com.apigateway.common.dto;

import com.apigateway.common.enums.OperationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class AuditLogDTO {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditLogResponse {
        private Long id;
        private String logId;
        private Long tenantId;
        private String tenantName;
        private String resourceType;
        private String resourceId;
        private OperationType operationType;
        private String operatorId;
        private String operatorName;
        private String operatorIp;
        private String beforeValue;
        private String afterValue;
        private Boolean success;
        private String errorMessage;
        private String requestUri;
        private String requestMethod;
        private LocalDateTime createdAt;
    }
}
