package com.apigateway.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class ApplicationDTO {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApplicationRequest {
        @NotBlank(message = "Application ID is required")
        @Size(max = 64, message = "Application ID must be at most 64 characters")
        private String appId;

        @NotBlank(message = "Name is required")
        @Size(max = 128, message = "Name must be at most 128 characters")
        private String name;

        @Size(max = 512, message = "Description must be at most 512 characters")
        private String description;

        @NotNull(message = "Tenant ID is required")
        private Long tenantId;

        private Boolean enabled;

        @Size(max = 256, message = "Base URL must be at most 256 characters")
        private String baseUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApplicationResponse {
        private Long id;
        private String appId;
        private String name;
        private String description;
        private Long tenantId;
        private String tenantName;
        private Boolean enabled;
        private String baseUrl;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
