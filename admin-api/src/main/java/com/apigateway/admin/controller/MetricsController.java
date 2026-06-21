package com.apigateway.admin.controller;

import com.apigateway.admin.service.MetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final MetricsService metricsService;

    @GetMapping("/qps")
    public ResponseEntity<Map<String, Object>> getQpsMetrics(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String appId,
            @RequestParam(required = false) Integer minutes) {
        return ResponseEntity.ok(metricsService.getQpsMetrics(tenantId, appId, minutes));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatusMetrics(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String appId,
            @RequestParam(required = false) Integer minutes) {
        return ResponseEntity.ok(metricsService.getStatusMetrics(tenantId, appId, minutes));
    }

    @GetMapping("/latency")
    public ResponseEntity<Map<String, Object>> getLatencyMetrics(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String appId,
            @RequestParam(required = false) Integer minutes) {
        return ResponseEntity.ok(metricsService.getLatencyMetrics(tenantId, appId, minutes));
    }

    @GetMapping("/logs")
    public ResponseEntity<Map<String, Object>> getRecentLogs(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String appId,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(metricsService.getRecentLogs(tenantId, appId, limit));
    }
}
