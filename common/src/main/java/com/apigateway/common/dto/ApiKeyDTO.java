package com.apigateway.common.dto;

import com.apigateway.common.enums.ApiKeyStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class ApiKeyDTO {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiKeyRequest {
        @NotBlank(message = "Key ID is required")
        @Size(max = 64, message = "Key ID must be at most 64 characters")
        private String keyId;

        @NotBlank(message = "Name is required")
        @Size(max = 128, message = "Name must be at most 128 characters")
        private String name;

        @NotNull(message = "Tenant ID is required")
        private Long tenantId;

        private Long applicationId;

        private ApiKeyStatus status;

        private LocalDateTime expiresAt;

        @Size(max = 1024, message = "Allowed IPs must be at most 1024 characters")
        private String allowedIps;

        @NotNull(message = "Rate limit per second is required")
        private Long rateLimitPerSecond;

        @NotNull(message = "Rate limit per day is required")
        private Long rateLimitPerDay;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiKeyResponse {
        private Long id;
        private String keyId;
        private String name;
        private String apiKey;
        private Long tenantId;
        private String tenantName;
        private Long applicationId;
        private String applicationName;
        private ApiKeyStatus status;
        private LocalDateTime expiresAt;
        private String allowedIps;
        private Long rateLimitPerSecond;
        private Long rateLimitPerDay;
        private String createdBy;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
