package com.apigateway.common.dto;

import com.apigateway.common.entity.TargetBackend;
import com.apigateway.common.enums.HttpMethod;
import com.apigateway.common.enums.RuleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class RouteRuleVersionDTO {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteRuleVersionResponse {
        private Long id;
        private String versionId;
        private Long routeRuleId;
        private Integer version;
        private String name;
        private String description;
        private String path;
        private HttpMethod method;
        private String targetUrl;
        private Integer priority;
        private RuleStatus status;
        private Boolean requiresAuth;
        private Boolean rateLimitEnabled;
        private Boolean circuitBreakerEnabled;
        private String configSnapshot;
        private String changeLog;
        private String createdBy;
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
    }
}
