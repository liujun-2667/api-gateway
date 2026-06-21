package com.apigateway.admin.controller;

import com.apigateway.common.dto.RateLimitConfigDTO;
import com.apigateway.admin.service.RateLimitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/apps/{appId}/rate-limits")
@RequiredArgsConstructor
public class RateLimitController {

    private final RateLimitService rateLimitService;

    @GetMapping
    public ResponseEntity<List<RateLimitConfigDTO.RateLimitConfigResponse>> getRateLimits(@PathVariable Long appId) {
        return ResponseEntity.ok(rateLimitService.getRateLimitsByAppId(appId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RateLimitConfigDTO.RateLimitConfigResponse> getRateLimitById(
            @PathVariable Long appId, @PathVariable Long id) {
        return ResponseEntity.ok(rateLimitService.getRateLimitById(id));
    }

    @PostMapping
    public ResponseEntity<RateLimitConfigDTO.RateLimitConfigResponse> createRateLimit(
            @PathVariable Long appId,
            @Valid @RequestBody RateLimitConfigDTO.RateLimitConfigRequest request) {
        return new ResponseEntity<>(rateLimitService.createRateLimit(appId, request), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<RateLimitConfigDTO.RateLimitConfigResponse> updateRateLimit(
            @PathVariable Long appId,
            @PathVariable Long id,
            @Valid @RequestBody RateLimitConfigDTO.RateLimitConfigRequest request) {
        return ResponseEntity.ok(rateLimitService.updateRateLimit(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRateLimit(@PathVariable Long appId, @PathVariable Long id) {
        rateLimitService.deleteRateLimit(id);
        return ResponseEntity.noContent().build();
    }
}
