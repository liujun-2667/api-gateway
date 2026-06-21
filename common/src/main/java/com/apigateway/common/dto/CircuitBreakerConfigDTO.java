package com.apigateway.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class CircuitBreakerConfigDTO {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CircuitBreakerConfigRequest {
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

        @NotNull(message = "Failure rate threshold is required")
        private Float failureRateThreshold;

        @NotNull(message = "Slow call rate threshold is required")
        private Float slowCallRateThreshold;

        @NotNull(message = "Slow call duration threshold is required")
        private Long slowCallDurationThreshold;

        @NotNull(message = "Permitted number of calls in half-open state is required")
        private Integer permittedNumberOfCallsInHalfOpenState;

        @NotNull(message = "Sliding window size is required")
        private Integer slidingWindowSize;

        @NotBlank(message = "Sliding window type is required")
        @Size(max = 32, message = "Sliding window type must be at most 32 characters")
        private String slidingWindowType;

        @NotNull(message = "Minimum number of calls is required")
        private Integer minimumNumberOfCalls;

        @NotNull(message = "Wait duration in open state is required")
        private Long waitDurationInOpenState;

        @Size(max = 1024, message = "Fallback URL must be at most 1024 characters")
        private String fallbackUrl;

        private Boolean enabled;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CircuitBreakerConfigResponse {
        private Long id;
        private String configId;
        private String name;
        private String description;
        private Long tenantId;
        private String tenantName;
        private Long routeRuleId;
        private String routeRuleName;
        private Float failureRateThreshold;
        private Float slowCallRateThreshold;
        private Long slowCallDurationThreshold;
        private Integer permittedNumberOfCallsInHalfOpenState;
        private Integer slidingWindowSize;
        private String slidingWindowType;
        private Integer minimumNumberOfCalls;
        private Long waitDurationInOpenState;
        private String fallbackUrl;
        private Boolean enabled;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
