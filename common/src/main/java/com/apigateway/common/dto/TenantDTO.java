package com.apigateway.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class TenantDTO {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenantRequest {
        @NotBlank(message = "Tenant ID is required")
        @Size(max = 64, message = "Tenant ID must be at most 64 characters")
        private String tenantId;

        @NotBlank(message = "Name is required")
        @Size(max = 128, message = "Name must be at most 128 characters")
        private String name;

        @Size(max = 512, message = "Description must be at most 512 characters")
        private String description;

        private Boolean enabled;

        @Size(max = 256, message = "Contact email must be at most 256 characters")
        private String contactEmail;

        @Size(max = 32, message = "Contact phone must be at most 32 characters")
        private String contactPhone;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenantResponse {
        private Long id;
        private String tenantId;
        private String name;
        private String description;
        private Boolean enabled;
        private String contactEmail;
        private String contactPhone;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
