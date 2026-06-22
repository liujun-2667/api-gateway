package com.apigateway.common.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

public class VersionDiffDTO {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiffRequest {
        @NotNull(message = "Version 1 ID is required")
        private Long version1Id;

        @NotNull(message = "Version 2 ID is required")
        private Long version2Id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiffField {
        private String fieldName;
        private String oldValue;
        private String newValue;
        private String changeType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiffResponse {
        private Long version1Id;
        private Long version2Id;
        private List<DiffField> diffs;
        private Map<String, List<DiffField>> diffsByCategory;
    }
}
