package com.apigateway.common.dto;

import com.apigateway.common.entity.TargetBackend;
import com.apigateway.common.enums.HttpMethod;
import com.apigateway.common.enums.RuleStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class RouteRuleDTO {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteRuleRequest {
        @NotBlank(message = "Rule ID is required")
        @Size(max = 64, message = "Rule ID must be at most 64 characters")
        private String ruleId;

        @NotBlank(message = "Name is required")
        @Size(max = 128, message = "Name must be at most 128 characters")
        private String name;

        @Size(max = 512, message = "Description must be at most 512 characters")
        private String description;

        @NotNull(message = "Tenant ID is required")
        private Long tenantId;

        @NotNull(message = "Application ID is required")
        private Long applicationId;

        @NotBlank(message = "Path is required")
        @Size(max = 512, message = "Path must be at most 512 characters")
        private String path;

        @NotNull(message = "HTTP method is required")
        private HttpMethod method;

        @Size(max = 512, message = "Target URL must be at most 512 characters")
        private String targetUrl;

        @NotNull(message = "Priority is required")
        private Integer priority;

        private RuleStatus status;

        private Boolean requiresAuth;

        private Boolean rateLimitEnabled;

        private Boolean circuitBreakerEnabled;

        private LocalDateTime effectiveFrom;

        private LocalDateTime effectiveTo;

        private List<TargetBackend> targetBackends;

        private Integer connectTimeoutMs;

        private Integer readTimeoutMs;

        private Integer maxRetries;

        private Boolean retryOn5xx;

        private Boolean retryOnTimeout;

        private Integer retryIntervalMs;

        private Map<String, String> requestHeadersToAdd;

        private List<String> requestHeadersToRemove;

        private String pathPrefixReplacement;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteRuleResponse {
        private Long id;
        private String ruleId;
        private String name;
        private String description;
        private Long tenantId;
        private String tenantName;
        private Long applicationId;
        private String applicationName;
        private String path;
        private HttpMethod method;
        private String targetUrl;
        private Integer priority;
        private RuleStatus status;
        private Boolean requiresAuth;
        private Boolean rateLimitEnabled;
        private Boolean circuitBreakerEnabled;
        private LocalDateTime effectiveFrom;
        private LocalDateTime effectiveTo;
        private String createdBy;
        private String updatedBy;
        private List<TargetBackend> targetBackends;
        private Integer connectTimeoutMs;
        private Integer readTimeoutMs;
        private Integer maxRetries;
        private Boolean retryOn5xx;
        private Boolean retryOnTimeout;
        private Integer retryIntervalMs;
        private Map<String, String> requestHeadersToAdd;
        private List<String> requestHeadersToRemove;
        private String pathPrefixReplacement;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
