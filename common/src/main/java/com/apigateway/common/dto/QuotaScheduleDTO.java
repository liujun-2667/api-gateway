package com.apigateway.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalTime;

public class QuotaScheduleDTO {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuotaScheduleRequest {
        @NotBlank(message = "Schedule ID is required")
        @Size(max = 64, message = "Schedule ID must be at most 64 characters")
        private String scheduleId;

        @NotBlank(message = "Name is required")
        @Size(max = 128, message = "Name must be at most 128 characters")
        private String name;

        @Size(max = 512, message = "Description must be at most 512 characters")
        private String description;

        @NotNull(message = "Tenant ID is required")
        private Long tenantId;

        private Long apiKeyId;

        private Long rateLimitConfigId;

        @NotBlank(message = "Schedule type is required")
        @Size(max = 16, message = "Schedule type must be at most 16 characters")
        private String scheduleType;

        @Size(max = 32, message = "Days of week must be at most 32 characters")
        private String daysOfWeek;

        private LocalTime startTime;

        private LocalTime endTime;

        @NotNull(message = "Adjusted requests per second is required")
        private Long adjustedRequestsPerSecond;

        @NotNull(message = "Adjusted requests per minute is required")
        private Long adjustedRequestsPerMinute;

        @NotNull(message = "Adjusted requests per hour is required")
        private Long adjustedRequestsPerHour;

        @NotNull(message = "Adjusted requests per day is required")
        private Long adjustedRequestsPerDay;

        private Boolean enabled;

        private LocalDateTime effectiveFrom;

        private LocalDateTime effectiveTo;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuotaScheduleResponse {
        private Long id;
        private String scheduleId;
        private String name;
        private String description;
        private Long tenantId;
        private String tenantName;
        private Long apiKeyId;
        private String apiKeyName;
        private Long rateLimitConfigId;
        private String rateLimitConfigName;
        private String scheduleType;
        private String daysOfWeek;
        private LocalTime startTime;
        private LocalTime endTime;
        private Long adjustedRequestsPerSecond;
        private Long adjustedRequestsPerMinute;
        private Long adjustedRequestsPerHour;
        private Long adjustedRequestsPerDay;
        private Boolean enabled;
        private LocalDateTime effectiveFrom;
        private LocalDateTime effectiveTo;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
