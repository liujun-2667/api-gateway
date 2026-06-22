package com.apigateway.common.dto;

import com.apigateway.common.enums.DocStatus;
import com.apigateway.common.enums.HttpMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ApiDocDTO {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiDocCreateRequest {
        @NotBlank(message = "Doc name is required")
        @Size(max = 128, message = "Name must be at most 128 characters")
        private String name;

        @Size(max = 512, message = "Description must be at most 512 characters")
        private String description;

        @Size(max = 32, message = "Version must be at most 32 characters")
        private String version;

        @NotNull(message = "Application ID is required")
        private Long applicationId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiDocUpdateRequest {
        @Size(max = 128, message = "Name must be at most 128 characters")
        private String name;

        @Size(max = 512, message = "Description must be at most 512 characters")
        private String description;

        @Size(max = 32, message = "Version must be at most 32 characters")
        private String version;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiDocResponse {
        private Long id;
        private String docId;
        private String name;
        private String description;
        private String version;
        private Long applicationId;
        private String applicationName;
        private DocStatus status;
        private String createdBy;
        private String updatedBy;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private List<ApiDocGroupResponse> groups;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiDocGroupResponse {
        private Long id;
        private String name;
        private String description;
        private Integer sortOrder;
        private Long docId;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private List<ApiEndpointResponse> endpoints;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiDocGroupCreateRequest {
        @NotBlank(message = "Group name is required")
        @Size(max = 128, message = "Name must be at most 128 characters")
        private String name;

        @Size(max = 512, message = "Description must be at most 512 characters")
        private String description;

        private Integer sortOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiEndpointCreateRequest {
        @NotBlank(message = "Endpoint name is required")
        @Size(max = 128, message = "Name must be at most 128 characters")
        private String name;

        @Size(max = 512, message = "Description must be at most 512 characters")
        private String description;

        @NotNull(message = "HTTP method is required")
        private HttpMethod method;

        @NotBlank(message = "Path is required")
        @Size(max = 512, message = "Path must be at most 512 characters")
        private String path;

        private Integer sortOrder;

        private List<Map<String, Object>> requestParams;

        private Map<String, Object> requestSchema;

        private Map<String, Object> responseSchema;

        private List<Map<String, Object>> statusCodes;

        private String deprecated;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiEndpointUpdateRequest {
        @Size(max = 128, message = "Name must be at most 128 characters")
        private String name;

        @Size(max = 512, message = "Description must be at most 512 characters")
        private String description;

        private HttpMethod method;

        @Size(max = 512, message = "Path must be at most 512 characters")
        private String path;

        private Integer sortOrder;

        private List<Map<String, Object>> requestParams;

        private Map<String, Object> requestSchema;

        private Map<String, Object> responseSchema;

        private List<Map<String, Object>> statusCodes;

        private String deprecated;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiEndpointResponse {
        private Long id;
        private String name;
        private String description;
        private HttpMethod method;
        private String path;
        private Integer sortOrder;
        private Long groupId;
        private String groupName;
        private List<Map<String, Object>> requestParams;
        private Map<String, Object> requestSchema;
        private Map<String, Object> responseSchema;
        private List<Map<String, Object>> statusCodes;
        private String deprecated;
        private MockConfigResponse mockConfig;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MockConfigUpdateRequest {
        private Boolean enabled;

        private Integer delayMs;

        private Integer faultInjectionPercent;

        private String faultErrorCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MockConfigResponse {
        private Long id;
        private String mockConfigId;
        private Long endpointId;
        private Long routeRuleId;
        private String routeRuleName;
        private Boolean enabled;
        private Integer delayMs;
        private Integer faultInjectionPercent;
        private String faultErrorCode;
        private String createdBy;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DebugCaseCreateRequest {
        @NotBlank(message = "Case name is required")
        @Size(max = 128, message = "Name must be at most 128 characters")
        private String name;

        @Size(max = 512, message = "Description must be at most 512 characters")
        private String description;

        @NotNull(message = "Endpoint ID is required")
        private Long endpointId;

        private Map<String, Object> requestParams;

        private Map<String, Object> requestHeaders;

        private Object requestBody;

        private Map<String, Object> expectedResponse;

        private Boolean useMock;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DebugCaseUpdateRequest {
        @Size(max = 128, message = "Name must be at most 128 characters")
        private String name;

        @Size(max = 512, message = "Description must be at most 512 characters")
        private String description;

        private Map<String, Object> requestParams;

        private Map<String, Object> requestHeaders;

        private Object requestBody;

        private Map<String, Object> expectedResponse;

        private Boolean useMock;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DebugCaseResponse {
        private Long id;
        private String name;
        private String description;
        private Long endpointId;
        private String endpointName;
        private Map<String, Object> requestParams;
        private Map<String, Object> requestHeaders;
        private Object requestBody;
        private Map<String, Object> expectedResponse;
        private Boolean useMock;
        private String createdBy;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiChangeRecordResponse {
        private Long id;
        private Long endpointId;
        private String endpointName;
        private String changeType;
        private String changeSummary;
        private List<Map<String, Object>> changeDetails;
        private String changedBy;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OpenApiImportRequest {
        @NotNull(message = "OpenAPI content is required")
        private String openApiContent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DebugRequest {
        @NotNull(message = "Endpoint ID is required")
        private Long endpointId;

        private Boolean useMock;

        private Map<String, Object> requestParams;

        private Map<String, Object> requestHeaders;

        private Object requestBody;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DebugResponse {
        private Integer statusCode;
        private Map<String, String> responseHeaders;
        private Object responseBody;
        private Long latencyMs;
        private Boolean isMock;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchReplayRequest {
        @NotNull(message = "Case IDs are required")
        private List<Long> caseIds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchReplayResult {
        private Long caseId;
        private String caseName;
        private Boolean success;
        private String message;
        private DebugResponse actualResponse;
        private Map<String, Object> diffResult;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChangeNotification {
        private Long docId;
        private Long endpointId;
        private String endpointName;
        private String changeType;
        private String changeSummary;
        private LocalDateTime timestamp;
    }
}
