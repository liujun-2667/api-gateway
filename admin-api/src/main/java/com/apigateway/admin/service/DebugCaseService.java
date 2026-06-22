package com.apigateway.admin.service;

import com.apigateway.admin.repository.ApiEndpointRepository;
import com.apigateway.admin.repository.DebugCaseRepository;
import com.apigateway.common.dto.ApiDocDTO;
import com.apigateway.common.entity.ApiEndpoint;
import com.apigateway.common.entity.DebugCase;
import com.apigateway.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DebugCaseService {

    private final DebugCaseRepository debugCaseRepository;
    private final ApiEndpointRepository apiEndpointRepository;

    @Transactional
    public ApiDocDTO.DebugCaseResponse createDebugCase(ApiDocDTO.DebugCaseCreateRequest request, String createdBy) {
        ApiEndpoint endpoint = apiEndpointRepository.findById(request.getEndpointId())
                .orElseThrow(() -> new NotFoundException("ApiEndpoint", request.getEndpointId().toString()));

        DebugCase debugCase = DebugCase.builder()
                .name(request.getName())
                .description(request.getDescription())
                .endpoint(endpoint)
                .requestParams(request.getRequestParams())
                .requestHeaders(request.getRequestHeaders())
                .requestBody(request.getRequestBody())
                .expectedResponse(request.getExpectedResponse())
                .useMock(request.getUseMock() != null ? request.getUseMock() : true)
                .createdBy(createdBy)
                .build();

        debugCase = debugCaseRepository.save(debugCase);
        return toResponse(debugCase);
    }

    @Transactional(readOnly = true)
    public List<ApiDocDTO.DebugCaseResponse> getDebugCasesByEndpoint(Long endpointId) {
        if (!apiEndpointRepository.existsById(endpointId)) {
            throw new NotFoundException("ApiEndpoint", endpointId.toString());
        }
        return debugCaseRepository.findByEndpointIdOrderByCreatedAtDesc(endpointId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ApiDocDTO.DebugCaseResponse getDebugCase(Long id) {
        DebugCase debugCase = debugCaseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("DebugCase", id.toString()));
        return toResponse(debugCase);
    }

    @Transactional
    public ApiDocDTO.DebugCaseResponse updateDebugCase(Long id, ApiDocDTO.DebugCaseUpdateRequest request, String updatedBy) {
        DebugCase debugCase = debugCaseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("DebugCase", id.toString()));

        if (request.getName() != null) debugCase.setName(request.getName());
        if (request.getDescription() != null) debugCase.setDescription(request.getDescription());
        if (request.getRequestParams() != null) debugCase.setRequestParams(request.getRequestParams());
        if (request.getRequestHeaders() != null) debugCase.setRequestHeaders(request.getRequestHeaders());
        if (request.getRequestBody() != null) debugCase.setRequestBody(request.getRequestBody());
        if (request.getExpectedResponse() != null) debugCase.setExpectedResponse(request.getExpectedResponse());
        if (request.getUseMock() != null) debugCase.setUseMock(request.getUseMock());

        debugCase = debugCaseRepository.save(debugCase);
        return toResponse(debugCase);
    }

    @Transactional
    public void deleteDebugCase(Long id) {
        if (!debugCaseRepository.existsById(id)) {
            throw new NotFoundException("DebugCase", id.toString());
        }
        debugCaseRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<ApiDocDTO.BatchReplayResult> batchReplay(ApiDocDTO.BatchReplayRequest request) {
        List<ApiDocDTO.BatchReplayResult> results = new ArrayList<>();
        for (Long caseId : request.getCaseIds()) {
            try {
                DebugCase debugCase = debugCaseRepository.findById(caseId).orElse(null);
                if (debugCase == null) {
                    results.add(ApiDocDTO.BatchReplayResult.builder()
                            .caseId(caseId)
                            .success(false)
                            .message("Debug case not found")
                            .build());
                    continue;
                }

                ApiDocDTO.BatchReplayResult result = ApiDocDTO.BatchReplayResult.builder()
                        .caseId(caseId)
                        .caseName(debugCase.getName())
                        .success(true)
                        .message("Replay executed (actual HTTP call would be made in production)")
                        .build();

                if (debugCase.getExpectedResponse() != null) {
                    result.setDiffResult(Map.of(
                            "status", "pending_comparison",
                            "expectedDefined", true
                    ));
                }

                results.add(result);
            } catch (Exception e) {
                results.add(ApiDocDTO.BatchReplayResult.builder()
                        .caseId(caseId)
                        .success(false)
                        .message("Error: " + e.getMessage())
                        .build());
            }
        }
        return results;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> compareResponses(Map<String, Object> expected, Map<String, Object> actual) {
        Map<String, Object> diff = new LinkedHashMap<>();
        List<Map<String, Object>> differences = new ArrayList<>();

        Set<String> allKeys = new HashSet<>();
        if (expected != null) allKeys.addAll(expected.keySet());
        if (actual != null) allKeys.addAll(actual.keySet());

        for (String key : allKeys) {
            Object expectedVal = expected != null ? expected.get(key) : null;
            Object actualVal = actual != null ? actual.get(key) : null;

            if (expectedVal == null && actualVal != null) {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("field", key);
                d.put("changeType", "ADD");
                d.put("newValue", actualVal);
                differences.add(d);
            } else if (expectedVal != null && actualVal == null) {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("field", key);
                d.put("changeType", "REMOVE");
                d.put("oldValue", expectedVal);
                differences.add(d);
            } else if (expectedVal != null && !expectedVal.equals(actualVal)) {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("field", key);
                d.put("changeType", "MODIFY");
                d.put("oldValue", expectedVal);
                d.put("newValue", actualVal);
                differences.add(d);
            }
        }

        diff.put("match", differences.isEmpty());
        diff.put("differences", differences);
        return diff;
    }

    private ApiDocDTO.DebugCaseResponse toResponse(DebugCase dc) {
        return ApiDocDTO.DebugCaseResponse.builder()
                .id(dc.getId())
                .name(dc.getName())
                .description(dc.getDescription())
                .endpointId(dc.getEndpoint() != null ? dc.getEndpoint().getId() : null)
                .endpointName(dc.getEndpoint() != null ? dc.getEndpoint().getName() : null)
                .requestParams(dc.getRequestParams())
                .requestHeaders(dc.getRequestHeaders())
                .requestBody(dc.getRequestBody())
                .expectedResponse(dc.getExpectedResponse())
                .useMock(dc.getUseMock())
                .createdBy(dc.getCreatedBy())
                .createdAt(dc.getCreatedAt())
                .updatedAt(dc.getUpdatedAt())
                .build();
    }
}
