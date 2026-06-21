package com.apigateway.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class RateLimitConfigDTO {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RateLimitConfigRequest {
        @NotBlank(message = "Config ID is required")
        @Size(max = 64, message = "Config ID must be at most 64 characters")
        private String configId;

        @NotBlank(message = "Name is required")
        @Size(max = 128, message = "Name must be at most 128 characters")
        private String name;

        @Size(max = 512, message = "Description must be at most 512 characters")
        private String description;

        @NotNull(message = "Tenant ID is required")
        private Long tenantId;

        private Long routeRuleId;

        private Long apiKeyId;

        @NotNull(message = "Requests per second is required")
        private Long requestsPerSecond;

        @NotNull(message = "Requests per minute is required")
        private Long requestsPerMinute;

        @NotNull(message = "Requests per hour is required")
        private Long requestsPerHour;

        @NotNull(message = "Requests per day is required")
        private Long requestsPerDay;

        @NotNull(message = "Burst capacity is required")
        private Long burstCapacity;

        private Boolean enabled;

        @Size(max = 64, message = "Scope must be at most 64 characters")
        private String scope;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RateLimitConfigResponse {
        private Long id;
        private String configId;
        private String name;
        private String description;
        private Long tenantId;
        private String tenantName;
        private Long routeRuleId;
        private String routeRuleName;
        private Long apiKeyId;
        private String apiKeyName;
        private Long requestsPerSecond;
        private Long requestsPerMinute;
        private Long requestsPerHour;
        private Long requestsPerDay;
        private Long burstCapacity;
        private Boolean enabled;
        private String scope;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
