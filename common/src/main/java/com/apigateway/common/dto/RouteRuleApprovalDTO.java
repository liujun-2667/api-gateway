package com.apigateway.common.dto;

import com.apigateway.common.enums.ApprovalStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class RouteRuleApprovalDTO {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteRuleApprovalRequest {
        @NotNull(message = "Route rule ID is required")
        private Long routeRuleId;

        @NotNull(message = "Route rule version ID is required")
        private Long routeRuleVersionId;

        @Size(max = 1024, message = "Request comment must be at most 1024 characters")
        private String requestComment;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApprovalActionRequest {
        @NotNull(message = "Approval status is required")
        private ApprovalStatus status;

        @Size(max = 1024, message = "Approval comment must be at most 1024 characters")
        private String approvalComment;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteRuleApprovalResponse {
        private Long id;
        private String approvalId;
        private Long routeRuleId;
        private String routeRuleName;
        private Long routeRuleVersionId;
        private Integer routeRuleVersion;
        private ApprovalStatus status;
        private String approverId;
        private String approverName;
        private String approvalComment;
        private LocalDateTime approvedAt;
        private String requesterId;
        private String requesterName;
        private String requestComment;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
