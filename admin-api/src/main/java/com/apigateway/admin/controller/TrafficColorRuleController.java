package com.apigateway.admin.controller;

import com.apigateway.common.dto.GrayReleaseDTO;
import com.apigateway.common.dto.TrafficColorRuleDTO;
import com.apigateway.admin.service.GrayReleaseService;
import com.apigateway.admin.service.TrafficColorRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/apps/{appId}/color-rules")
@RequiredArgsConstructor
public class TrafficColorRuleController {

    private final TrafficColorRuleService trafficColorRuleService;
    private final GrayReleaseService grayReleaseService;

    @GetMapping
    public ResponseEntity<List<TrafficColorRuleDTO.TrafficColorRuleResponse>> getTrafficColorRules(@PathVariable Long appId) {
        return ResponseEntity.ok(trafficColorRuleService.getTrafficColorRulesByAppId(appId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TrafficColorRuleDTO.TrafficColorRuleResponse> getTrafficColorRuleById(
            @PathVariable Long appId, @PathVariable Long id) {
        return ResponseEntity.ok(trafficColorRuleService.getTrafficColorRuleById(id));
    }

    @PostMapping
    public ResponseEntity<TrafficColorRuleDTO.TrafficColorRuleResponse> createTrafficColorRule(
            @PathVariable Long appId,
            @Valid @RequestBody TrafficColorRuleDTO.TrafficColorRuleRequest request) {
        return new ResponseEntity<>(trafficColorRuleService.createTrafficColorRule(appId, request), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TrafficColorRuleDTO.TrafficColorRuleResponse> updateTrafficColorRule(
            @PathVariable Long appId,
            @PathVariable Long id,
            @Valid @RequestBody TrafficColorRuleDTO.TrafficColorRuleRequest request) {
        return ResponseEntity.ok(trafficColorRuleService.updateTrafficColorRule(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTrafficColorRule(@PathVariable Long appId, @PathVariable Long id) {
        trafficColorRuleService.deleteTrafficColorRule(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/enable-all")
    public ResponseEntity<Map<String, Object>> enableAllRules(
            @PathVariable Long appId,
            @RequestParam Long tenantId) {
        int count = trafficColorRuleService.enableAllRules(tenantId);
        return ResponseEntity.ok(Map.of("enabled", count));
    }

    @PostMapping("/disable-all")
    public ResponseEntity<Map<String, Object>> disableAllRules(
            @PathVariable Long appId,
            @RequestParam Long tenantId) {
        int count = trafficColorRuleService.disableAllRules(tenantId);
        return ResponseEntity.ok(Map.of("disabled", count));
    }

    @DeleteMapping("/clear-all")
    public ResponseEntity<Map<String, Object>> clearAllRules(
            @PathVariable Long appId,
            @RequestParam Long tenantId) {
        int count = trafficColorRuleService.clearAllRules(tenantId);
        return ResponseEntity.ok(Map.of("deleted", count));
    }

    @GetMapping("/gray-releases")
    public ResponseEntity<List<GrayReleaseDTO.GrayReleaseResponse>> getActiveGrayReleases(@PathVariable Long appId) {
        return ResponseEntity.ok(grayReleaseService.getActiveGrayReleases());
    }
}
