package com.apigateway.admin.service;

import com.apigateway.admin.repository.*;
import com.apigateway.common.dto.ApiDocDTO;
import com.apigateway.common.entity.*;
import com.apigateway.common.exception.NotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestSuiteService {

    private final TestSuiteRepository testSuiteRepository;
    private final TestSuiteExecutionRepository executionRepository;
    private final TestReportRepository testReportRepository;
    private final DebugCaseRepository debugCaseRepository;
    private final ApplicationRepository applicationRepository;
    private final ApiEndpointRepository endpointRepository;
    private final DebugCaseService debugCaseService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${gateway.core.url:http://localhost:8080}")
    private String gatewayCoreUrl;

    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final Map<Long, Semaphore> executionLocks = new ConcurrentHashMap<>();

    @Transactional
    public ApiDocDTO.TestSuiteResponse createTestSuite(ApiDocDTO.TestSuiteCreateRequest request, String createdBy) {
        Application application = applicationRepository.findById(request.getApplicationId())
                .orElseThrow(() -> new NotFoundException("Application", request.getApplicationId().toString()));

        TestSuite suite = TestSuite.builder()
                .name(request.getName())
                .description(request.getDescription())
                .application(application)
                .caseOrder(request.getCaseOrder())
                .dependencies(request.getDependencies())
                .globalVariables(request.getGlobalVariables())
                .concurrencyLevel(request.getConcurrencyLevel() != null ? request.getConcurrencyLevel() : 1)
                .createdBy(createdBy)
                .build();

        suite = testSuiteRepository.save(suite);
        return toSuiteResponse(suite);
    }

    @Transactional(readOnly = true)
    public List<ApiDocDTO.TestSuiteResponse> getTestSuitesByAppId(Long applicationId) {
        if (!applicationRepository.existsById(applicationId)) {
            throw new NotFoundException("Application", applicationId.toString());
        }
        return testSuiteRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId).stream()
                .map(this::toSuiteResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ApiDocDTO.TestSuiteResponse getTestSuite(Long id) {
        TestSuite suite = testSuiteRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("TestSuite", id.toString()));
        return toSuiteResponse(suite);
    }

    @Transactional
    public ApiDocDTO.TestSuiteResponse updateTestSuite(Long id, ApiDocDTO.TestSuiteUpdateRequest request, String updatedBy) {
        TestSuite suite = testSuiteRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("TestSuite", id.toString()));

        if (request.getName() != null) suite.setName(request.getName());
        if (request.getDescription() != null) suite.setDescription(request.getDescription());
        if (request.getCaseOrder() != null) suite.setCaseOrder(request.getCaseOrder());
        if (request.getDependencies() != null) suite.setDependencies(request.getDependencies());
        if (request.getGlobalVariables() != null) suite.setGlobalVariables(request.getGlobalVariables());
        if (request.getConcurrencyLevel() != null) suite.setConcurrencyLevel(request.getConcurrencyLevel());

        suite = testSuiteRepository.save(suite);
        return toSuiteResponse(suite);
    }

    @Transactional
    public void deleteTestSuite(Long id) {
        if (!testSuiteRepository.existsById(id)) {
            throw new NotFoundException("TestSuite", id.toString());
        }
        testSuiteRepository.deleteById(id);
    }

    @Transactional
    public ApiDocDTO.TestSuiteExecutionResponse executeTestSuite(Long suiteId, String executedBy) {
        TestSuite suite = testSuiteRepository.findById(suiteId)
                .orElseThrow(() -> new NotFoundException("TestSuite", suiteId.toString()));

        List<Map<String, Object>> caseOrder = suite.getCaseOrder();
        if (caseOrder == null || caseOrder.isEmpty()) {
            throw new IllegalArgumentException("Test suite has no cases");
        }

        int totalCases = caseOrder.size();

        TestSuiteExecution execution = TestSuiteExecution.builder()
                .testSuite(suite)
                .status("PENDING")
                .totalCases(totalCases)
                .passedCases(0)
                .failedCases(0)
                .caseResults(new ArrayList<>())
                .executedBy(executedBy)
                .build();

        execution = executionRepository.save(execution);

        TestSuiteExecution finalExecution = execution;
        CompletableFuture.runAsync(() -> {
            try {
                runExecution(suite, finalExecution);
            } catch (Exception e) {
                log.error("Test suite execution failed", e);
                finalExecution.setStatus("FAILED");
                finalExecution.setCompletedAt(LocalDateTime.now());
                executionRepository.save(finalExecution);
            }
        }, executorService);

        return toExecutionResponse(execution);
    }

    @SuppressWarnings("unchecked")
    private void runExecution(TestSuite suite, TestSuiteExecution execution) {
        Semaphore lock = executionLocks.computeIfAbsent(execution.getId(), k -> new Semaphore(1));
        try {
            lock.acquire();
            execution.setStatus("RUNNING");
            executionRepository.save(execution);
            sendProgress(execution);

            long startTime = System.currentTimeMillis();
            List<Map<String, Object>> caseOrder = suite.getCaseOrder();
            List<Map<String, Object>> dependencies = suite.getDependencies();
            Map<String, Object> globalVariables = suite.getGlobalVariables();
            int concurrencyLevel = suite.getConcurrencyLevel() != null ? suite.getConcurrencyLevel() : 1;

            Map<Long, Map<String, Object>> caseResults = new ConcurrentHashMap<>();
            Map<Long, String> caseStatuses = new ConcurrentHashMap<>();
            Set<Long> completedCases = Collections.newSetFromMap(new ConcurrentHashMap<>());

            List<Long> orderedCaseIds = caseOrder.stream()
                    .map(m -> ((Number) m.get("caseId")).longValue())
                    .collect(Collectors.toList());

            Map<Long, DebugCase> debugCaseMap = new HashMap<>();
            for (Long caseId : orderedCaseIds) {
                debugCaseRepository.findById(caseId).ifPresent(dc -> debugCaseMap.put(caseId, dc));
            }

            Map<Long, List<Long>> dependencyGraph = buildDependencyGraph(dependencies, orderedCaseIds);

            List<Long> sortedCaseIds = topologicalSort(orderedCaseIds, dependencyGraph);
            if (sortedCaseIds == null) {
                throw new IllegalArgumentException("Circular dependency detected in test suite");
            }

            ExecutorService caseExecutor = Executors.newFixedThreadPool(Math.max(concurrencyLevel, 1));
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (Long caseId : sortedCaseIds) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        waitForDependencies(caseId, dependencyGraph, completedCases, caseStatuses);

                        DebugCase debugCase = debugCaseMap.get(caseId);
                        if (debugCase == null) {
                            caseStatuses.put(caseId, "FAILED");
                            Map<String, Object> result = new LinkedHashMap<>();
                            result.put("caseId", caseId);
                            result.put("status", "FAILED");
                            result.put("errorMessage", "Debug case not found");
                            caseResults.put(caseId, result);
                            completedCases.add(caseId);
                            updateExecutionProgress(execution, caseResults, caseStatuses, startTime);
                            return;
                        }

                        caseStatuses.put(caseId, "RUNNING");
                        sendCaseProgress(execution.getId(), caseId, debugCase.getName(), "RUNNING", null, null);

                        long caseStartTime = System.currentTimeMillis();
                        ApiDocDTO.DebugResponse response = executeDebugCase(debugCase, globalVariables);
                        long caseDuration = System.currentTimeMillis() - caseStartTime;

                        Map<String, Object> diffResult = null;
                        boolean passed = true;
                        String errorMessage = null;

                        if (debugCase.getExpectedResponse() != null) {
                            Map<String, Object> expected = debugCase.getExpectedResponse();
                            Map<String, Object> actual = response.getResponseBody() instanceof Map
                                    ? (Map<String, Object>) response.getResponseBody()
                                    : null;
                            diffResult = debugCaseService.compareResponses(expected, actual);
                            passed = (Boolean) diffResult.getOrDefault("match");
                            if (!passed) {
                                errorMessage = "Response mismatch";
                            }
                        }

                        if (response.getStatusCode() >= 400) {
                            passed = false;
                            errorMessage = "HTTP error: " + response.getStatusCode();
                        }

                        String status = passed ? "PASSED" : "FAILED";
                        caseStatuses.put(caseId, status);

                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("caseId", caseId);
                        result.put("caseName", debugCase.getName());
                        result.put("status", status);
                        result.put("durationMs", caseDuration);
                        result.put("response", response);
                        result.put("diffResult", diffResult);
                        result.put("errorMessage", errorMessage);
                        caseResults.put(caseId, result);
                        completedCases.add(caseId);

                        sendCaseProgress(execution.getId(), caseId, debugCase.getName(), status, diffResult, errorMessage);
                        updateExecutionProgress(execution, caseResults, caseStatuses, startTime);

                    } catch (Exception e) {
                        log.error("Case execution failed: " + caseId, e);
                        caseStatuses.put(caseId, "FAILED");
                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("caseId", caseId);
                        result.put("status", "FAILED");
                        result.put("errorMessage", e.getMessage());
                        caseResults.put(caseId, result);
                        completedCases.add(caseId);
                        updateExecutionProgress(execution, caseResults, caseStatuses, startTime);
                    }
                }, caseExecutor);
                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            caseExecutor.shutdown();

            long totalDuration = System.currentTimeMillis() - startTime;
            execution.setTotalDurationMs(totalDuration);

            int passed = (int) caseStatuses.values().stream().filter("PASSED"::equals).count();
            int failed = (int) caseStatuses.values().stream().filter("FAILED"::equals).count();

            execution.setPassedCases(passed);
            execution.setFailedCases(failed);
            execution.setStatus(failed > 0 ? "COMPLETED_WITH_FAILURES" : "COMPLETED");
            execution.setCompletedAt(LocalDateTime.now());

            List<Map<String, Object>> orderedResults = orderedCaseIds.stream()
                    .map(caseResults::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            execution.setCaseResults(orderedResults);

            executionRepository.save(execution);
            sendProgress(execution);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Execution interrupted", e);
        } finally {
            lock.release();
            executionLocks.remove(execution.getId());
        }
    }

    private Map<Long, List<Long>> buildDependencyGraph(List<Map<String, Object>> dependencies, List<Long> caseIds) {
        Map<Long, List<Long>> graph = new HashMap<>();
        if (dependencies != null) {
            for (Map<String, Object> dep : dependencies) {
                Long caseId = ((Number) dep.get("caseId")).longValue();
                Long dependsOn = ((Number) dep.get("dependsOn")).longValue();
                if (caseIds.contains(caseId) && caseIds.contains(dependsOn)) {
                    graph.computeIfAbsent(caseId, k -> new ArrayList<>()).add(dependsOn);
                }
            }
        }
        return graph;
    }

    private List<Long> topologicalSort(List<Long> caseIds, Map<Long, List<Long>> dependencyGraph) {
        Map<Long, Integer> inDegree = new HashMap<>();
        Map<Long, List<Long>> reverseGraph = new HashMap<>();

        for (Long caseId : caseIds) {
            inDegree.put(caseId, 0);
            reverseGraph.put(caseId, new ArrayList<>());
        }

        for (Map.Entry<Long, List<Long>> entry : dependencyGraph.entrySet()) {
            Long caseId = entry.getKey();
            for (Long dep : entry.getValue()) {
                if (caseIds.contains(dep)) {
                    inDegree.put(caseId, inDegree.getOrDefault(caseId, 0) + 1);
                    reverseGraph.computeIfAbsent(dep, k -> new ArrayList<>()).add(caseId);
                }
            }
        }

        Queue<Long> queue = new LinkedList<>();
        for (Long caseId : caseIds) {
            if (inDegree.getOrDefault(caseId, 0) == 0) {
                queue.offer(caseId);
            }
        }

        List<Long> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            result.add(current);
            for (Long next : reverseGraph.getOrDefault(current, Collections.emptyList())) {
                int degree = inDegree.get(next) - 1;
                inDegree.put(next, degree);
                if (degree == 0) {
                    queue.offer(next);
                }
            }
        }

        return result.size() == caseIds.size() ? result : null;
    }

    private void waitForDependencies(Long caseId, Map<Long, List<Long>> dependencyGraph,
                                   Set<Long> completedCases, Map<Long, String> caseStatuses) throws InterruptedException {
        List<Long> deps = dependencyGraph.get(caseId);
        if (deps == null || deps.isEmpty()) {
            return;
        }

        long maxWaitTimeMs = 5 * 60 * 1000;
        long startTime = System.currentTimeMillis();

        for (Long depId : deps) {
            while (!completedCases.contains(depId)) {
                if (System.currentTimeMillis() - startTime > maxWaitTimeMs) {
                    throw new RuntimeException("Dependency case " + depId + " timed out after " +
                            (maxWaitTimeMs / 1000) + " seconds");
                }

                if ("FAILED".equals(caseStatuses.get(depId))) {
                    throw new RuntimeException("Dependency case " + depId + " failed");
                }
                if ("SKIPPED".equals(caseStatuses.get(depId))) {
                    throw new RuntimeException("Dependency case " + depId + " was skipped");
                }

                Thread.sleep(200);
            }

            String depStatus = caseStatuses.get(depId);
            if ("FAILED".equals(depStatus)) {
                throw new RuntimeException("Dependency case " + depId + " failed");
            }
            if ("SKIPPED".equals(depStatus)) {
                throw new RuntimeException("Dependency case " + depId + " was skipped");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private ApiDocDTO.DebugResponse executeDebugCase(DebugCase debugCase, Map<String, Object> globalVariables) {
        long startTime = System.currentTimeMillis();
        ApiEndpoint endpoint = debugCase.getEndpoint();
        String url = gatewayCoreUrl + endpoint.getPath();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        if (debugCase.getRequestHeaders() != null) {
            for (Map.Entry<String, Object> entry : debugCase.getRequestHeaders().entrySet()) {
                headers.set(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : "");
            }
        }

        if (globalVariables != null) {
            for (Map.Entry<String, Object> entry : globalVariables.entrySet()) {
                headers.set(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : "");
            }
        }

        Object bodyObj = debugCase.getRequestBody();
        String body = null;
        if (bodyObj != null) {
            try {
                body = objectMapper.writeValueAsString(bodyObj);
            } catch (Exception e) {
                body = bodyObj.toString();
            }
        }

        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Object> response = restTemplate.exchange(
                    url,
                    org.springframework.http.HttpMethod.valueOf(endpoint.getMethod().name()),
                    entity,
                    Object.class);

            long latency = System.currentTimeMillis() - startTime;

            Map<String, String> respHeaders = new LinkedHashMap<>();
            if (response.getHeaders() != null) {
                for (Map.Entry<String, List<String>> entry : response.getHeaders().entrySet()) {
                    respHeaders.put(entry.getKey(), String.join(",", entry.getValue()));
                }
            }

            return ApiDocDTO.DebugResponse.builder()
                    .statusCode(response.getStatusCode().value())
                    .responseHeaders(respHeaders)
                    .responseBody(response.getBody())
                    .latencyMs(latency)
                    .isMock(false)
                    .build();
        } catch (Exception e) {
            log.error("Debug request failed", e);
            long latency = System.currentTimeMillis() - startTime;
            Map<String, String> respHeaders = new LinkedHashMap<>();
            respHeaders.put("Content-Type", "application/json");
            Map<String, Object> errorBody = new LinkedHashMap<>();
            errorBody.put("error", "Debug request failed");
            errorBody.put("message", e.getMessage());
            return ApiDocDTO.DebugResponse.builder()
                    .statusCode(500)
                    .responseHeaders(respHeaders)
                    .responseBody(errorBody)
                    .latencyMs(latency)
                    .isMock(false)
                    .build();
        }
    }

    private void updateExecutionProgress(TestSuiteExecution execution,
                                       Map<Long, Map<String, Object>> caseResults,
                                       Map<Long, String> caseStatuses,
                                       long startTime) {
        int passed = (int) caseStatuses.values().stream().filter("PASSED"::equals).count();
        int failed = (int) caseStatuses.values().stream().filter("FAILED"::equals).count();
        execution.setPassedCases(passed);
        execution.setFailedCases(failed);
        execution.setCaseResults(new ArrayList<>(caseResults.values()));
        execution.setTotalDurationMs(System.currentTimeMillis() - startTime);
        executionRepository.save(execution);
        sendProgress(execution);
    }

    private void sendProgress(TestSuiteExecution execution) {
        try {
            messagingTemplate.convertAndSend(
                    "/topic/test-suites/" + execution.getTestSuite().getId() + "/executions/" + execution.getId() + "/progress",
                    toExecutionResponse(execution));
        } catch (Exception e) {
            log.warn("Failed to send progress", e);
        }
    }

    private void sendCaseProgress(Long executionId, Long caseId, String caseName, String status,
                                  Map<String, Object> diffResult, String errorMessage) {
        try {
            ApiDocDTO.CaseExecutionProgress progress = ApiDocDTO.CaseExecutionProgress.builder()
                    .caseId(caseId)
                    .caseName(caseName)
                    .status(status)
                    .diffResult(diffResult)
                    .errorMessage(errorMessage)
                    .build();
            messagingTemplate.convertAndSend(
                    "/topic/test-suites/executions/" + executionId + "/cases/" + caseId + "/progress",
                    progress);
        } catch (Exception e) {
            log.warn("Failed to send case progress", e);
        }
    }

    @Transactional(readOnly = true)
    public List<ApiDocDTO.TestSuiteExecutionResponse> getExecutions(Long suiteId) {
        if (!testSuiteRepository.existsById(suiteId)) {
            throw new NotFoundException("TestSuite", suiteId.toString());
        }
        return executionRepository.findByTestSuiteIdOrderByCreatedAtDesc(suiteId).stream()
                .map(this::toExecutionResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ApiDocDTO.TestSuiteExecutionResponse getExecution(Long id) {
        TestSuiteExecution execution = executionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("TestSuiteExecution", id.toString()));
        return toExecutionResponse(execution);
    }

    @Transactional
    public ApiDocDTO.TestReportResponse saveReport(ApiDocDTO.TestReportCreateRequest request, String createdBy) {
        TestSuite suite = testSuiteRepository.findById(request.getTestSuiteId())
                .orElseThrow(() -> new NotFoundException("TestSuite", request.getTestSuiteId().toString()));

        TestSuiteExecution execution = executionRepository.findById(request.getExecutionId())
                .orElseThrow(() -> new NotFoundException("TestSuiteExecution", request.getExecutionId().toString()));

        int total = execution.getTotalCases();
        int passed = execution.getPassedCases() != null ? execution.getPassedCases() : 0;
        int failed = execution.getFailedCases() != null ? execution.getFailedCases() : 0;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalCases", total);
        summary.put("passedCases", passed);
        summary.put("failedCases", failed);
        summary.put("successRate", total > 0 ? (double) passed / total * 100 : 0.0);
        summary.put("totalDurationMs", execution.getTotalDurationMs());

        TestReport report = TestReport.builder()
                .name(request.getName())
                .testSuite(suite)
                .execution(execution)
                .totalCases(total)
                .passedCases(passed)
                .failedCases(failed)
                .successRate(total > 0 ? (double) passed / total * 100 : 0.0)
                .totalDurationMs(execution.getTotalDurationMs() != null ? execution.getTotalDurationMs() : 0L)
                .caseDetails(execution.getCaseResults())
                .summary(summary)
                .remarks(request.getRemarks())
                .createdBy(createdBy)
                .build();

        report = testReportRepository.save(report);
        return toReportResponse(report);
    }

    @Transactional(readOnly = true)
    public List<ApiDocDTO.TestReportResponse> getReports(Long suiteId) {
        if (!testSuiteRepository.existsById(suiteId)) {
            throw new NotFoundException("TestSuite", suiteId.toString());
        }
        return testReportRepository.findByTestSuiteIdOrderByCreatedAtDesc(suiteId).stream()
                .map(this::toReportResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ApiDocDTO.TestReportResponse getReport(Long id) {
        TestReport report = testReportRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("TestReport", id.toString()));
        return toReportResponse(report);
    }

    private ApiDocDTO.TestSuiteResponse toSuiteResponse(TestSuite suite) {
        return ApiDocDTO.TestSuiteResponse.builder()
                .id(suite.getId())
                .name(suite.getName())
                .description(suite.getDescription())
                .applicationId(suite.getApplication() != null ? suite.getApplication().getId() : null)
                .applicationName(suite.getApplication() != null ? suite.getApplication().getName() : null)
                .caseOrder(suite.getCaseOrder())
                .dependencies(suite.getDependencies())
                .globalVariables(suite.getGlobalVariables())
                .concurrencyLevel(suite.getConcurrencyLevel())
                .createdBy(suite.getCreatedBy())
                .createdAt(suite.getCreatedAt())
                .updatedAt(suite.getUpdatedAt())
                .build();
    }

    private ApiDocDTO.TestSuiteExecutionResponse toExecutionResponse(TestSuiteExecution execution) {
        return ApiDocDTO.TestSuiteExecutionResponse.builder()
                .id(execution.getId())
                .testSuiteId(execution.getTestSuite() != null ? execution.getTestSuite().getId() : null)
                .testSuiteName(execution.getTestSuite() != null ? execution.getTestSuite().getName() : null)
                .status(execution.getStatus())
                .totalCases(execution.getTotalCases())
                .passedCases(execution.getPassedCases())
                .failedCases(execution.getFailedCases())
                .totalDurationMs(execution.getTotalDurationMs())
                .caseResults(execution.getCaseResults())
                .executedBy(execution.getExecutedBy())
                .createdAt(execution.getCreatedAt())
                .updatedAt(execution.getUpdatedAt())
                .completedAt(execution.getCompletedAt())
                .build();
    }

    private ApiDocDTO.TestReportResponse toReportResponse(TestReport report) {
        return ApiDocDTO.TestReportResponse.builder()
                .id(report.getId())
                .name(report.getName())
                .testSuiteId(report.getTestSuite() != null ? report.getTestSuite().getId() : null)
                .testSuiteName(report.getTestSuite() != null ? report.getTestSuite().getName() : null)
                .executionId(report.getExecution() != null ? report.getExecution().getId() : null)
                .totalCases(report.getTotalCases())
                .passedCases(report.getPassedCases())
                .failedCases(report.getFailedCases())
                .successRate(report.getSuccessRate())
                .totalDurationMs(report.getTotalDurationMs())
                .caseDetails(report.getCaseDetails())
                .summary(report.getSummary())
                .remarks(report.getRemarks())
                .createdBy(report.getCreatedBy())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt())
                .build();
    }
}
