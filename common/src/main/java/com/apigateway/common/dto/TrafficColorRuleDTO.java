package com.apigateway.common.dto;

import com.apigateway.common.enums.ColorTagOperation;
import com.apigateway.common.enums.MatchType;
import com.apigateway.common.enums.RuleStatus;
import com.apigateway.common.enums.TrafficConditionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class TrafficColorRuleDTO {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrafficColorRuleRequest {
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

        private Long routeRuleId;

        @NotNull(message = "Condition type is required")
        private TrafficConditionType conditionType;

        @Size(max = 128, message = "Condition key must be at most 128 characters")
        private String conditionKey;

        @NotNull(message = "Match type is required")
        private MatchType matchType;

        @NotBlank(message = "Condition value is required")
        @Size(max = 512, message = "Condition value must be at most 512 characters")
        private String conditionValue;

        @NotBlank(message = "Color tag is required")
        @Size(max = 64, message = "Color tag must be at most 64 characters")
        private String colorTag;

        @NotNull(message = "Operation is required")
        private ColorTagOperation operation;

        @NotNull(message = "Priority is required")
        private Integer priority;

        private RuleStatus status;

        private LocalDateTime effectiveFrom;

        private LocalDateTime effectiveTo;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrafficColorRuleResponse {
        private Long id;
        private String ruleId;
        private String name;
        private String description;
        private Long tenantId;
        private String tenantName;
        private Long routeRuleId;
        private String routeRuleName;
        private TrafficConditionType conditionType;
        private String conditionKey;
        private MatchType matchType;
        private String conditionValue;
        private String colorTag;
        private ColorTagOperation operation;
        private Integer priority;
        private RuleStatus status;
        private LocalDateTime effectiveFrom;
        private LocalDateTime effectiveTo;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
