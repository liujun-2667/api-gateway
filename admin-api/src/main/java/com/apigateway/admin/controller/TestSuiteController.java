package com.apigateway.admin.controller;

import com.apigateway.admin.service.TestSuiteService;
import com.apigateway.admin.service.VersionCompareService;
import com.apigateway.admin.util.SecurityUtil;
import com.apigateway.common.dto.ApiDocDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TestSuiteController {

    private final VersionCompareService versionCompareService;
    private final TestSuiteService testSuiteService;
    private final SecurityUtil securityUtil;

    @PostMapping("/version-compare")
    public ResponseEntity<ApiDocDTO.VersionCompareResponse> compareVersions(
            @Valid @RequestBody ApiDocDTO.VersionCompareRequest request) {
        return ResponseEntity.ok(versionCompareService.compareVersions(request));
    }

    @PostMapping("/change-records/{changeRecordId}/remarks")
    public ResponseEntity<ApiDocDTO.ChangeRemarkResponse> addRemark(
            @PathVariable Long changeRecordId,
            @Valid @RequestBody ApiDocDTO.ChangeRemarkRequest request) {
        String createdBy = securityUtil.getCurrentUsername();
        return new ResponseEntity<>(
                versionCompareService.addRemark(changeRecordId, request, createdBy),
                HttpStatus.CREATED);
    }

    @GetMapping("/change-records/{changeRecordId}/remarks")
    public ResponseEntity<List<ApiDocDTO.ChangeRemarkResponse>> getRemarks(
            @PathVariable Long changeRecordId) {
        return ResponseEntity.ok(versionCompareService.getRemarks(changeRecordId));
    }

    @DeleteMapping("/change-records/remarks/{remarkId}")
    public ResponseEntity<Void> deleteRemark(@PathVariable Long remarkId) {
        versionCompareService.deleteRemark(remarkId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/apps/{appId}/test-suites")
    public ResponseEntity<ApiDocDTO.TestSuiteResponse> createTestSuite(
            @PathVariable Long appId,
            @Valid @RequestBody ApiDocDTO.TestSuiteCreateRequest request) {
        String createdBy = securityUtil.getCurrentUsername();
        return new ResponseEntity<>(
                testSuiteService.createTestSuite(request, createdBy),
                HttpStatus.CREATED);
    }

    @GetMapping("/apps/{appId}/test-suites")
    public ResponseEntity<List<ApiDocDTO.TestSuiteResponse>> getTestSuites(
            @PathVariable Long appId) {
        return ResponseEntity.ok(testSuiteService.getTestSuitesByAppId(appId));
    }

    @GetMapping("/test-suites/{id}")
    public ResponseEntity<ApiDocDTO.TestSuiteResponse> getTestSuite(@PathVariable Long id) {
        return ResponseEntity.ok(testSuiteService.getTestSuite(id));
    }

    @PutMapping("/test-suites/{id}")
    public ResponseEntity<ApiDocDTO.TestSuiteResponse> updateTestSuite(
            @PathVariable Long id,
            @Valid @RequestBody ApiDocDTO.TestSuiteUpdateRequest request) {
        String updatedBy = securityUtil.getCurrentUsername();
        return ResponseEntity.ok(testSuiteService.updateTestSuite(id, request, updatedBy));
    }

    @DeleteMapping("/test-suites/{id}")
    public ResponseEntity<Void> deleteTestSuite(@PathVariable Long id) {
        testSuiteService.deleteTestSuite(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test-suites/{id}/execute")
    public ResponseEntity<ApiDocDTO.TestSuiteExecutionResponse> executeTestSuite(
            @PathVariable Long id) {
        String executedBy = securityUtil.getCurrentUsername();
        return new ResponseEntity<>(
                testSuiteService.executeTestSuite(id, executedBy),
                HttpStatus.ACCEPTED);
    }

    @GetMapping("/test-suites/{id}/executions")
    public ResponseEntity<List<ApiDocDTO.TestSuiteExecutionResponse>> getExecutions(
            @PathVariable Long id) {
        return ResponseEntity.ok(testSuiteService.getExecutions(id));
    }

    @GetMapping("/test-suites/executions/{id}")
    public ResponseEntity<ApiDocDTO.TestSuiteExecutionResponse> getExecution(
            @PathVariable Long id) {
        return ResponseEntity.ok(testSuiteService.getExecution(id));
    }

    @PostMapping("/test-suites/reports")
    public ResponseEntity<ApiDocDTO.TestReportResponse> saveReport(
            @Valid @RequestBody ApiDocDTO.TestReportCreateRequest request) {
        String createdBy = securityUtil.getCurrentUsername();
        return new ResponseEntity<>(
                testSuiteService.saveReport(request, createdBy),
                HttpStatus.CREATED);
    }

    @GetMapping("/test-suites/{id}/reports")
    public ResponseEntity<List<ApiDocDTO.TestReportResponse>> getReports(
            @PathVariable Long id) {
        return ResponseEntity.ok(testSuiteService.getReports(id));
    }

    @GetMapping("/test-suites/reports/{id}")
    public ResponseEntity<ApiDocDTO.TestReportResponse> getReport(@PathVariable Long id) {
        return ResponseEntity.ok(testSuiteService.getReport(id));
    }
}
