package com.apigateway.admin.controller;

import com.apigateway.common.dto.ApiKeyDTO;
import com.apigateway.admin.service.ApiKeyService;
import com.apigateway.admin.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenants/{tenantId}/keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final SecurityUtil securityUtil;

    @GetMapping
    public ResponseEntity<List<ApiKeyDTO.ApiKeyResponse>> getApiKeys(@PathVariable Long tenantId) {
        return ResponseEntity.ok(apiKeyService.getApiKeysByTenantId(tenantId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiKeyDTO.ApiKeyResponse> getApiKeyById(@PathVariable Long tenantId, @PathVariable Long id) {
        return ResponseEntity.ok(apiKeyService.getApiKeyById(id));
    }

    @PostMapping
    public ResponseEntity<ApiKeyDTO.ApiKeyResponse> createApiKey(
            @PathVariable Long tenantId,
            @Valid @RequestBody ApiKeyDTO.ApiKeyRequest request) {
        String createdBy = securityUtil.getCurrentUsername();
        return new ResponseEntity<>(apiKeyService.createApiKey(tenantId, request, createdBy), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiKeyDTO.ApiKeyResponse> updateApiKey(
            @PathVariable Long tenantId,
            @PathVariable Long id,
            @Valid @RequestBody ApiKeyDTO.ApiKeyRequest request) {
        return ResponseEntity.ok(apiKeyService.updateApiKey(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteApiKey(@PathVariable Long tenantId, @PathVariable Long id) {
        apiKeyService.deleteApiKey(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/enable")
    public ResponseEntity<ApiKeyDTO.ApiKeyResponse> enableApiKey(@PathVariable Long tenantId, @PathVariable Long id) {
        return ResponseEntity.ok(apiKeyService.enableApiKey(id));
    }

    @PostMapping("/{id}/disable")
    public ResponseEntity<ApiKeyDTO.ApiKeyResponse> disableApiKey(@PathVariable Long tenantId, @PathVariable Long id) {
        return ResponseEntity.ok(apiKeyService.disableApiKey(id));
    }

    @PostMapping("/{id}/rotate")
    public ResponseEntity<ApiKeyDTO.ApiKeyResponse> rotateApiKey(@PathVariable Long tenantId, @PathVariable Long id) {
        String createdBy = securityUtil.getCurrentUsername();
        return ResponseEntity.ok(apiKeyService.rotateApiKey(id, createdBy));
    }
}
