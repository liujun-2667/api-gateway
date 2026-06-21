package com.apigateway.admin.controller;

import com.apigateway.common.dto.CircuitBreakerConfigDTO;
import com.apigateway.admin.service.CircuitBreakerConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/apps/{appId}/circuit-breakers")
@RequiredArgsConstructor
public class CircuitBreakerController {

    private final CircuitBreakerConfigService circuitBreakerConfigService;

    @GetMapping
    public ResponseEntity<List<CircuitBreakerConfigDTO.CircuitBreakerConfigResponse>> getCircuitBreakers(@PathVariable Long appId) {
        return ResponseEntity.ok(circuitBreakerConfigService.getCircuitBreakersByAppId(appId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CircuitBreakerConfigDTO.CircuitBreakerConfigResponse> getCircuitBreakerById(
            @PathVariable Long appId, @PathVariable Long id) {
        return ResponseEntity.ok(circuitBreakerConfigService.getCircuitBreakerById(id));
    }

    @PostMapping
    public ResponseEntity<CircuitBreakerConfigDTO.CircuitBreakerConfigResponse> createCircuitBreaker(
            @PathVariable Long appId,
            @Valid @RequestBody CircuitBreakerConfigDTO.CircuitBreakerConfigRequest request) {
        return new ResponseEntity<>(circuitBreakerConfigService.createCircuitBreaker(appId, request), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CircuitBreakerConfigDTO.CircuitBreakerConfigResponse> updateCircuitBreaker(
            @PathVariable Long appId,
            @PathVariable Long id,
            @Valid @RequestBody CircuitBreakerConfigDTO.CircuitBreakerConfigRequest request) {
        return ResponseEntity.ok(circuitBreakerConfigService.updateCircuitBreaker(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCircuitBreaker(@PathVariable Long appId, @PathVariable Long id) {
        circuitBreakerConfigService.deleteCircuitBreaker(id);
        return ResponseEntity.noContent().build();
    }
}
