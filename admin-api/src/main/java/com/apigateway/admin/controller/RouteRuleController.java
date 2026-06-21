package com.apigateway.admin.controller;

import com.apigateway.common.dto.PageResponse;
import com.apigateway.common.dto.RouteRuleApprovalDTO;
import com.apigateway.common.dto.RouteRuleDTO;
import com.apigateway.common.dto.RouteRuleVersionDTO;
import com.apigateway.admin.service.ApprovalService;
import com.apigateway.admin.service.RouteRuleService;
import com.apigateway.admin.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/apps/{appId}/rules")
@RequiredArgsConstructor
public class RouteRuleController {

    private final RouteRuleService routeRuleService;
    private final ApprovalService approvalService;
    private final SecurityUtil securityUtil;

    @GetMapping
    public ResponseEntity<PageResponse<RouteRuleDTO.RouteRuleResponse>> getRouteRules(
            @PathVariable Long appId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String[] sort) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(parseSort(sort)));
        return ResponseEntity.ok(routeRuleService.getRouteRulesByAppId(appId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RouteRuleDTO.RouteRuleResponse> getRouteRuleById(@PathVariable Long appId, @PathVariable Long id) {
        return ResponseEntity.ok(routeRuleService.getRouteRuleById(id));
    }

    @GetMapping("/{id}/versions")
    public ResponseEntity<List<RouteRuleVersionDTO.RouteRuleVersionResponse>> getRouteRuleVersions(
            @PathVariable Long appId, @PathVariable Long id) {
        return ResponseEntity.ok(routeRuleService.getRouteRuleVersions(id));
    }

    @PostMapping
    public ResponseEntity<RouteRuleDTO.RouteRuleResponse> createRouteRule(
            @PathVariable Long appId,
            @Valid @RequestBody RouteRuleDTO.RouteRuleRequest request) {
        String createdBy = securityUtil.getCurrentUsername();
        return new ResponseEntity<>(routeRuleService.createRouteRule(appId, request, createdBy), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<RouteRuleDTO.RouteRuleResponse> updateRouteRule(
            @PathVariable Long appId,
            @PathVariable Long id,
            @Valid @RequestBody RouteRuleDTO.RouteRuleRequest request,
            @RequestParam(required = false) String changeLog) {
        String updatedBy = securityUtil.getCurrentUsername();
        return ResponseEntity.ok(routeRuleService.updateRouteRule(id, request, updatedBy, changeLog));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRouteRule(@PathVariable Long appId, @PathVariable Long id) {
        routeRuleService.deleteRouteRule(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<RouteRuleDTO.RouteRuleResponse> publishRouteRule(@PathVariable Long appId, @PathVariable Long id) {
        return ResponseEntity.ok(routeRuleService.publishRouteRule(id));
    }

    @PostMapping("/{id}/rollback")
    public ResponseEntity<RouteRuleDTO.RouteRuleResponse> rollbackRouteRule(
            @PathVariable Long appId,
            @PathVariable Long id,
            @RequestParam Integer version) {
        return ResponseEntity.ok(routeRuleService.rollbackRouteRule(id, version));
    }

    @GetMapping("/{id}/approvals")
    public ResponseEntity<List<RouteRuleApprovalDTO.RouteRuleApprovalResponse>> getApprovals(
            @PathVariable Long appId, @PathVariable Long id) {
        return ResponseEntity.ok(approvalService.getApprovalsByRuleId(id));
    }

    @PostMapping("/{id}/approvals")
    public ResponseEntity<RouteRuleApprovalDTO.RouteRuleApprovalResponse> requestApproval(
            @PathVariable Long appId,
            @PathVariable Long id,
            @Valid @RequestBody RouteRuleApprovalDTO.RouteRuleApprovalRequest request) {
        request.setRouteRuleId(id);
        return new ResponseEntity<>(approvalService.requestApproval(request), HttpStatus.CREATED);
    }

    @PutMapping("/{id}/approvals/{approvalId}")
    public ResponseEntity<RouteRuleApprovalDTO.RouteRuleApprovalResponse> approveApproval(
            @PathVariable Long appId,
            @PathVariable Long id,
            @PathVariable Long approvalId,
            @Valid @RequestBody RouteRuleApprovalDTO.ApprovalActionRequest request) {
        return ResponseEntity.ok(approvalService.approveApproval(approvalId, request));
    }

    @PostMapping("/{id}/emergency-publish")
    public ResponseEntity<RouteRuleApprovalDTO.RouteRuleApprovalResponse> emergencyPublish(
            @PathVariable Long appId,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String comment = body != null ? body.get("comment") : null;
        return ResponseEntity.ok(approvalService.emergencyPublish(id, comment));
    }

    private Sort.Order[] parseSort(String[] sort) {
        return java.util.Arrays.stream(sort)
                .map(s -> {
                    String[] parts = s.split(",");
                    String property = parts[0];
                    Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1])
                            ? Sort.Direction.DESC : Sort.Direction.ASC;
                    return new Sort.Order(direction, property);
                })
                .toArray(Sort.Order[]::new);
    }
}
