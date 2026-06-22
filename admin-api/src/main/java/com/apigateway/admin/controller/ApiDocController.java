package com.apigateway.admin.controller;

import com.apigateway.admin.service.ApiDocService;
import com.apigateway.admin.service.DebugCaseService;
import com.apigateway.admin.service.MockService;
import com.apigateway.admin.util.SecurityUtil;
import com.apigateway.common.dto.ApiDocDTO;
import com.apigateway.common.entity.ApiEndpoint;
import com.apigateway.common.entity.MockConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiDocController {

    private final ApiDocService apiDocService;
    private final MockService mockService;
    private final DebugCaseService debugCaseService;
    private final SecurityUtil securityUtil;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${gateway.core.url:http://localhost:8080}")
    private String gatewayCoreUrl;

    @PostMapping("/apps/{appId}/api-docs")
    public ResponseEntity<ApiDocDTO.ApiDocResponse> createApiDoc(
            @PathVariable Long appId,
            @Valid @RequestBody ApiDocDTO.ApiDocCreateRequest request) {
        String createdBy = securityUtil.getCurrentUsername();
        return new ResponseEntity<>(apiDocService.createApiDoc(request, createdBy), HttpStatus.CREATED);
    }

    @GetMapping("/apps/{appId}/api-docs")
    public ResponseEntity<List<ApiDocDTO.ApiDocResponse>> getApiDocsByAppId(@PathVariable Long appId) {
        return ResponseEntity.ok(apiDocService.getApiDocsByAppId(appId));
    }

    @GetMapping("/api-docs/{id}")
    public ResponseEntity<ApiDocDTO.ApiDocResponse> getApiDocById(@PathVariable Long id) {
        return ResponseEntity.ok(apiDocService.getApiDocById(id));
    }

    @PutMapping("/api-docs/{id}")
    public ResponseEntity<ApiDocDTO.ApiDocResponse> updateApiDoc(
            @PathVariable Long id,
            @Valid @RequestBody ApiDocDTO.ApiDocUpdateRequest request) {
        String updatedBy = securityUtil.getCurrentUsername();
        return ResponseEntity.ok(apiDocService.updateApiDoc(id, request, updatedBy));
    }

    @PostMapping("/api-docs/{id}/publish")
    public ResponseEntity<ApiDocDTO.ApiDocResponse> publishApiDoc(@PathVariable Long id) {
        String updatedBy = securityUtil.getCurrentUsername();
        return ResponseEntity.ok(apiDocService.publishApiDoc(id, updatedBy));
    }

    @DeleteMapping("/api-docs/{id}")
    public ResponseEntity<Void> deleteApiDoc(@PathVariable Long id) {
        apiDocService.deleteApiDoc(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/apps/{appId}/api-docs/import")
    public ResponseEntity<ApiDocDTO.ApiDocResponse> importOpenApi(
            @PathVariable Long appId,
            @Valid @RequestBody ApiDocDTO.OpenApiImportRequest request) {
        String createdBy = securityUtil.getCurrentUsername();
        return new ResponseEntity<>(apiDocService.importOpenApi(appId, request, createdBy), HttpStatus.CREATED);
    }

    @PostMapping("/api-docs/{docId}/groups")
    public ResponseEntity<ApiDocDTO.ApiDocGroupResponse> createGroup(
            @PathVariable Long docId,
            @Valid @RequestBody ApiDocDTO.ApiDocGroupCreateRequest request) {
        return new ResponseEntity<>(apiDocService.createGroup(docId, request), HttpStatus.CREATED);
    }

    @DeleteMapping("/api-docs/groups/{groupId}")
    public ResponseEntity<Void> deleteGroup(@PathVariable Long groupId) {
        apiDocService.deleteGroup(groupId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api-docs/groups/{groupId}/endpoints")
    public ResponseEntity<ApiDocDTO.ApiEndpointResponse> createEndpoint(
            @PathVariable Long groupId,
            @Valid @RequestBody ApiDocDTO.ApiEndpointCreateRequest request) {
        return new ResponseEntity<>(apiDocService.createEndpoint(groupId, request), HttpStatus.CREATED);
    }

    @PutMapping("/api-docs/endpoints/{endpointId}")
    public ResponseEntity<ApiDocDTO.ApiEndpointResponse> updateEndpoint(
            @PathVariable Long endpointId,
            @Valid @RequestBody ApiDocDTO.ApiEndpointUpdateRequest request) {
        String updatedBy = securityUtil.getCurrentUsername();
        return ResponseEntity.ok(apiDocService.updateEndpoint(endpointId, request, updatedBy));
    }

    @DeleteMapping("/api-docs/endpoints/{endpointId}")
    public ResponseEntity<Void> deleteEndpoint(@PathVariable Long endpointId) {
        apiDocService.deleteEndpoint(endpointId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api-docs/endpoints/{endpointId}/changes")
    public ResponseEntity<List<ApiDocDTO.ApiChangeRecordResponse>> getChangeHistory(@PathVariable Long endpointId) {
        return ResponseEntity.ok(apiDocService.getChangeHistory(endpointId));
    }

    @GetMapping("/api-docs/{docId}/changes")
    public ResponseEntity<List<ApiDocDTO.ApiChangeRecordResponse>> getDocChangeHistory(@PathVariable Long docId) {
        return ResponseEntity.ok(apiDocService.getDocChangeHistory(docId));
    }

    @PutMapping("/api-docs/endpoints/{endpointId}/mock-config")
    public ResponseEntity<ApiDocDTO.MockConfigResponse> updateMockConfig(
            @PathVariable Long endpointId,
            @Valid @RequestBody ApiDocDTO.MockConfigUpdateRequest request) {
        String updatedBy = securityUtil.getCurrentUsername();
        return ResponseEntity.ok(mockService.updateMockConfig(endpointId, request, updatedBy));
    }

    @GetMapping("/api-docs/endpoints/{endpointId}/mock-config")
    public ResponseEntity<ApiDocDTO.MockConfigResponse> getMockConfig(@PathVariable Long endpointId) {
        return ResponseEntity.ok(mockService.getMockConfig(endpointId));
    }

    @PostMapping("/api-docs/endpoints/{endpointId}/bind-route/{routeRuleId}")
    public ResponseEntity<ApiDocDTO.MockConfigResponse> bindRouteRule(
            @PathVariable Long endpointId,
            @PathVariable Long routeRuleId) {
        return ResponseEntity.ok(mockService.bindRouteRule(endpointId, routeRuleId));
    }

    @PostMapping("/api-docs/endpoints/{endpointId}/debug-cases")
    public ResponseEntity<ApiDocDTO.DebugCaseResponse> createDebugCase(
            @PathVariable Long endpointId,
            @Valid @RequestBody ApiDocDTO.DebugCaseCreateRequest request) {
        String createdBy = securityUtil.getCurrentUsername();
        return new ResponseEntity<>(debugCaseService.createDebugCase(request, createdBy), HttpStatus.CREATED);
    }

    @GetMapping("/api-docs/endpoints/{endpointId}/debug-cases")
    public ResponseEntity<List<ApiDocDTO.DebugCaseResponse>> getDebugCases(@PathVariable Long endpointId) {
        return ResponseEntity.ok(debugCaseService.getDebugCasesByEndpoint(endpointId));
    }

    @GetMapping("/api-docs/debug-cases/{id}")
    public ResponseEntity<ApiDocDTO.DebugCaseResponse> getDebugCase(@PathVariable Long id) {
        return ResponseEntity.ok(debugCaseService.getDebugCase(id));
    }

    @PutMapping("/api-docs/debug-cases/{id}")
    public ResponseEntity<ApiDocDTO.DebugCaseResponse> updateDebugCase(
            @PathVariable Long id,
            @Valid @RequestBody ApiDocDTO.DebugCaseUpdateRequest request) {
        String updatedBy = securityUtil.getCurrentUsername();
        return ResponseEntity.ok(debugCaseService.updateDebugCase(id, request, updatedBy));
    }

    @DeleteMapping("/api-docs/debug-cases/{id}")
    public ResponseEntity<Void> deleteDebugCase(@PathVariable Long id) {
        debugCaseService.deleteDebugCase(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api-docs/debug-cases/batch-replay")
    public ResponseEntity<List<ApiDocDTO.BatchReplayResult>> batchReplay(
            @Valid @RequestBody ApiDocDTO.BatchReplayRequest request) {
        return ResponseEntity.ok(debugCaseService.batchReplay(request));
    }

    @PostMapping("/apps/{appId}/api-docs")
    public ResponseEntity<ApiDocDTO.ApiDocResponse> createApiDoc(
            @PathVariable Long appId,
            @Valid @RequestBody ApiDocDTO.ApiDocCreateRequest request) {
        String createdBy = securityUtil.getCurrentUsername();
        return new ResponseEntity<>(apiDocService.createApiDoc(request, createdBy), HttpStatus.CREATED);
    }

    @GetMapping("/apps/{appId}/api-docs")
    public ResponseEntity<List<ApiDocDTO.ApiDocResponse>> getApiDocsByAppId(@PathVariable Long appId) {
        return ResponseEntity.ok(apiDocService.getApiDocsByAppId(appId));
    }

    @GetMapping("/api-docs/{id}")
    public ResponseEntity<ApiDocDTO.ApiDocResponse> getApiDocById(@PathVariable Long id) {
        return ResponseEntity.ok(apiDocService.getApiDocById(id));
    }

    @PutMapping("/api-docs/{id}")
    public ResponseEntity<ApiDocDTO.ApiDocResponse> updateApiDoc(
            @PathVariable Long id,
            @Valid @RequestBody ApiDocDTO.ApiDocUpdateRequest request) {
        String updatedBy = securityUtil.getCurrentUsername();
        return ResponseEntity.ok(apiDocService.updateApiDoc(id, request, updatedBy));
    }

    @PostMapping("/api-docs/{id}/publish")
    public ResponseEntity<ApiDocDTO.ApiDocResponse> publishApiDoc(@PathVariable Long id) {
        String updatedBy = securityUtil.getCurrentUsername();
        return ResponseEntity.ok(apiDocService.publishApiDoc(id, updatedBy));
    }

    @DeleteMapping("/api-docs/{id}")
    public ResponseEntity<Void> deleteApiDoc(@PathVariable Long id) {
        apiDocService.deleteApiDoc(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/apps/{appId}/api-docs/import")
    public ResponseEntity<ApiDocDTO.ApiDocResponse> importOpenApi(
            @PathVariable Long appId,
            @Valid @RequestBody ApiDocDTO.OpenApiImportRequest request) {
        String createdBy = securityUtil.getCurrentUsername();
        return new ResponseEntity<>(apiDocService.importOpenApi(appId, request, createdBy), HttpStatus.CREATED);
    }

    @PostMapping("/api-docs/{docId}/groups")
    public ResponseEntity<ApiDocDTO.ApiDocGroupResponse> createGroup(
            @PathVariable Long docId,
            @Valid @RequestBody ApiDocDTO.ApiDocGroupCreateRequest request) {
        return new ResponseEntity<>(apiDocService.createGroup(docId, request), HttpStatus.CREATED);
    }

    @DeleteMapping("/api-docs/groups/{groupId}")
    public ResponseEntity<Void> deleteGroup(@PathVariable Long groupId) {
        apiDocService.deleteGroup(groupId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api-docs/groups/{groupId}/endpoints")
    public ResponseEntity<ApiDocDTO.ApiEndpointResponse> createEndpoint(
            @PathVariable Long groupId,
            @Valid @RequestBody ApiDocDTO.ApiEndpointCreateRequest request) {
        return new ResponseEntity<>(apiDocService.createEndpoint(groupId, request), HttpStatus.CREATED);
    }

    @PutMapping("/api-docs/endpoints/{endpointId}")
    public ResponseEntity<ApiDocDTO.ApiEndpointResponse> updateEndpoint(
            @PathVariable Long endpointId,
            @Valid @RequestBody ApiDocDTO.ApiEndpointUpdateRequest request) {
        String updatedBy = securityUtil.getCurrentUsername();
        return ResponseEntity.ok(apiDocService.updateEndpoint(endpointId, request, updatedBy));
    }

    @DeleteMapping("/api-docs/endpoints/{endpointId}")
    public ResponseEntity<Void> deleteEndpoint(@PathVariable Long endpointId) {
        apiDocService.deleteEndpoint(endpointId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api-docs/endpoints/{endpointId}/changes")
    public ResponseEntity<List<ApiDocDTO.ApiChangeRecordResponse>> getChangeHistory(@PathVariable Long endpointId) {
        return ResponseEntity.ok(apiDocService.getChangeHistory(endpointId));
    }

    @GetMapping("/api-docs/{docId}/changes")
    public ResponseEntity<List<ApiDocDTO.ApiChangeRecordResponse>> getDocChangeHistory(@PathVariable Long docId) {
        return ResponseEntity.ok(apiDocService.getDocChangeHistory(docId));
    }

    @PutMapping("/api-docs/endpoints/{endpointId}/mock-config")
    public ResponseEntity<ApiDocDTO.MockConfigResponse> updateMockConfig(
            @PathVariable Long endpointId,
            @Valid @RequestBody ApiDocDTO.MockConfigUpdateRequest request) {
        String updatedBy = securityUtil.getCurrentUsername();
        return ResponseEntity.ok(mockService.updateMockConfig(endpointId, request, updatedBy));
    }

    @GetMapping("/api-docs/endpoints/{endpointId}/mock-config")
    public ResponseEntity<ApiDocDTO.MockConfigResponse> getMockConfig(@PathVariable Long endpointId) {
        return ResponseEntity.ok(mockService.getMockConfig(endpointId));
    }

    @PostMapping("/api-docs/endpoints/{endpointId}/bind-route/{routeRuleId}")
    public ResponseEntity<ApiDocDTO.MockConfigResponse> bindRouteRule(
            @PathVariable Long endpointId,
            @PathVariable Long routeRuleId) {
        return ResponseEntity.ok(mockService.bindRouteRule(endpointId, routeRuleId));
    }

    @PostMapping("/api-docs/endpoints/{endpointId}/debug-cases")
    public ResponseEntity<ApiDocDTO.DebugCaseResponse> createDebugCase(
            @PathVariable Long endpointId,
            @Valid @RequestBody ApiDocDTO.DebugCaseCreateRequest request) {
        String createdBy = securityUtil.getCurrentUsername();
        return new ResponseEntity<>(debugCaseService.createDebugCase(request, createdBy), HttpStatus.CREATED);
    }

    @GetMapping("/api-docs/endpoints/{endpointId}/debug-cases")
    public ResponseEntity<List<ApiDocDTO.DebugCaseResponse>> getDebugCases(@PathVariable Long endpointId) {
        return ResponseEntity.ok(debugCaseService.getDebugCasesByEndpoint(endpointId));
    }

    @GetMapping("/api-docs/debug-cases/{id}")
    public ResponseEntity<ApiDocDTO.DebugCaseResponse> getDebugCase(@PathVariable Long id) {
        return ResponseEntity.ok(debugCaseService.getDebugCase(id));
    }

    @PutMapping("/api-docs/debug-cases/{id}")
    public ResponseEntity<ApiDocDTO.DebugCaseResponse> updateDebugCase(
            @PathVariable Long id,
            @Valid @RequestBody ApiDocDTO.DebugCaseUpdateRequest request) {
        String updatedBy = securityUtil.getCurrentUsername();
        return ResponseEntity.ok(debugCaseService.updateDebugCase(id, request, updatedBy));
    }

    @DeleteMapping("/api-docs/debug-cases/{id}")
    public ResponseEntity<Void> deleteDebugCase(@PathVariable Long id) {
        debugCaseService.deleteDebugCase(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api-docs/debug-cases/batch-replay")
    public ResponseEntity<List<ApiDocDTO.BatchReplayResult>> batchReplay(
            @Valid @RequestBody ApiDocDTO.BatchReplayRequest request) {
        return ResponseEntity.ok(debugCaseService.batchReplay(request));
    }

    @PostMapping("/api-docs/debug")
    public ResponseEntity<ApiDocDTO.DebugResponse> sendDebugRequest(@Valid @RequestBody ApiDocDTO.DebugRequest request) {
        long startTime = System.currentTimeMillis();
        boolean useMock = Boolean.TRUE.equals(request.getUseMock());

        ApiEndpoint endpoint = apiDocService.getEndpointEntityById(request.getEndpointId());

        if (useMock) {
            Optional<MockConfig> mockConfigOpt = mockService.getMockConfigForEndpoint(request.getEndpointId());

            try {
                int delay = 0;
                if (mockConfigOpt.isPresent()) {
                    MockConfig mockConfig = mockConfigOpt.get();
                    delay = mockConfig.getDelayMs();
                }
                if (delay > 0) {
                    Thread.sleep(delay);
                }

                Object mockBody = mockService.generateMockResponse(endpoint);

                boolean shouldFault = false;
                int statusCode = 200;
                if (mockConfigOpt.isPresent()) {
                    MockConfig mockConfig = mockConfigOpt.get();
                    shouldFault = mockService.shouldInjectFault(mockConfig);
                    if (shouldFault && mockConfig.getFaultErrorCode() != null) {
                        try {
                            statusCode = Integer.parseInt(mockConfig.getFaultErrorCode());
                        } catch (NumberFormatException e) {
                            statusCode = 500;
                        }
                    }
                }

                Map<String, String> headers = new LinkedHashMap<>();
                headers.put("Content-Type", "application/json");
                headers.put("X-Mock-Response", "true");

                long latency = System.currentTimeMillis() - startTime;
                return ResponseEntity.ok(ApiDocDTO.DebugResponse.builder()
                        .statusCode(statusCode)
                        .responseHeaders(headers)
                        .responseBody(mockBody)
                        .latencyMs(latency)
                        .isMock(true)
                        .build());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        } else {
            try {
                String url = gatewayCoreUrl + endpoint.getPath();

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                if (request.getRequestHeaders() != null) {
                    for (Map.Entry<String, Object> entry : request.getRequestHeaders().entrySet()) {
                        headers.set(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : "");
                    }
                }

                Object bodyObj = request.getRequestBody();
                String body = null;
                if (bodyObj != null) {
                    body = objectMapper.writeValueAsString(bodyObj);
                }

                HttpEntity<String> entity = new HttpEntity<>(body, headers);

                ResponseEntity<Object> response = restTemplate.exchange(
                        url,
                        HttpMethod.valueOf(endpoint.getMethod().name()),
                        entity,
                        Object.class);

                long latency = System.currentTimeMillis() - startTime;

                Map<String, String> respHeaders = new LinkedHashMap<>();
                if (response.getHeaders() != null) {
                    for (Map.Entry<String, List<String>> entry : response.getHeaders().entrySet()) {
                        respHeaders.put(entry.getKey(), String.join(",", entry.getValue()));
                    }
                }

                return ResponseEntity.ok(ApiDocDTO.DebugResponse.builder()
                        .statusCode(response.getStatusCode().value())
                        .responseHeaders(respHeaders)
                        .responseBody(response.getBody())
                        .latencyMs(latency)
                        .isMock(false)
                        .build());
            } catch (Exception e) {
                log.error("Debug request failed", e);
                long latency = System.currentTimeMillis() - startTime;
                Map<String, String> respHeaders = new LinkedHashMap<>();
                respHeaders.put("Content-Type", "application/json");
                Map<String, Object> errorBody = new LinkedHashMap<>();
                errorBody.put("error", "Debug request failed");
                errorBody.put("message", e.getMessage());
                return ResponseEntity.ok(ApiDocDTO.DebugResponse.builder()
                        .statusCode(500)
                        .responseHeaders(respHeaders)
                        .responseBody(errorBody)
                        .latencyMs(latency)
                        .isMock(false)
                        .build());
            }
        }
    }
}
