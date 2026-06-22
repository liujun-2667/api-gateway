package com.apigateway.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class BatchOperationDTO {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchOperationRequest {
        @NotEmpty(message = "IDs are required")
        private List<Long> ids;

        @NotBlank(message = "Operation is required")
        private String operation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchOperationResult {
        @NotNull(message = "ID is required")
        private Long id;

        private Boolean success;
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchOperationResponse {
        private List<BatchOperationResult> results;
        private Integer successCount;
        private Integer failedCount;
    }
}
