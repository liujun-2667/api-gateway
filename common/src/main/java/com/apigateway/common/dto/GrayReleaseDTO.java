package com.apigateway.common.dto;

import com.apigateway.common.enums.GrayReleasePhase;
import com.apigateway.common.enums.GrayReleaseStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public class GrayReleaseDTO {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GrayReleaseWizardStep1 {
        @NotNull(message = "Application ID is required")
        private Long appId;

        @NotNull(message = "Route rule ID is required")
        private Long routeRuleId;

        @NotBlank(message = "Name is required")
        @Size(max = 128, message = "Name must be at most 128 characters")
        private String name;

        @Size(max = 512, message = "Description must be at most 512 characters")
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GrayReleaseWizardStep2 {
        @NotNull(message = "Initial percent is required")
        private Integer initialPercent;

        @NotNull(message = "Release stages are required")
        private List<Integer> releaseStages;

        @NotNull(message = "Observation minutes per stage is required")
        private Integer observationMinutesPerStage;

        @NotNull(message = "Error rate threshold is required")
        private Double errorRateThreshold;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GrayReleaseWizardStep3 {
        @NotNull(message = "Confirmation is required")
        private Boolean confirmation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GrayReleaseCreateRequest {
        @NotNull(message = "Step 1 is required")
        private GrayReleaseWizardStep1 step1;

        @NotNull(message = "Step 2 is required")
        private GrayReleaseWizardStep2 step2;

        @NotNull(message = "Step 3 is required")
        private GrayReleaseWizardStep3 step3;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GrayReleaseResponse {
        private Long id;
        private String grayReleaseId;
        private String name;
        private String description;
        private Long routeRuleId;
        private Long appId;
        private GrayReleaseStatus status;
        private GrayReleasePhase currentPhase;
        private Integer currentTrafficPercent;
        private Double errorRateThreshold;
        private Double currentErrorRate;
        private LocalDateTime nextStageTime;
        private Integer totalStages;
        private Integer completedStages;
        private LocalDateTime createdAt;
        private String createdBy;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GrayReleaseActionRequest {
        @NotBlank(message = "Action is required")
        private String action;

        @Size(max = 512, message = "Reason must be at most 512 characters")
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GrayReleaseStatusResponse {
        private GrayReleaseResponse grayRelease;
        private TrafficColorRuleDTO.TrafficColorRuleResponse colorRule;
        private RouteRuleDTO.RouteRuleResponse routeRule;
    }
}
