package com.apigateway.admin.controller;

import com.apigateway.common.dto.PageResponse;
import com.apigateway.common.dto.QuotaScheduleDTO;
import com.apigateway.common.dto.TenantDTO;
import com.apigateway.admin.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @GetMapping
    public ResponseEntity<PageResponse<TenantDTO.TenantResponse>> getAllTenants(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String[] sort) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(parseSort(sort)));
        return ResponseEntity.ok(tenantService.getAllTenants(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TenantDTO.TenantResponse> getTenantById(@PathVariable Long id) {
        return ResponseEntity.ok(tenantService.getTenantById(id));
    }

    @PostMapping
    public ResponseEntity<TenantDTO.TenantResponse> createTenant(@Valid @RequestBody TenantDTO.TenantRequest request) {
        return new ResponseEntity<>(tenantService.createTenant(request), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TenantDTO.TenantResponse> updateTenant(@PathVariable Long id,
                                                                  @Valid @RequestBody TenantDTO.TenantRequest request) {
        return ResponseEntity.ok(tenantService.updateTenant(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTenant(@PathVariable Long id) {
        tenantService.deleteTenant(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{tenantId}/quotas")
    public ResponseEntity<List<QuotaScheduleDTO.QuotaScheduleResponse>> getTenantQuotas(@PathVariable Long tenantId) {
        return ResponseEntity.ok(tenantService.getTenantQuotas(tenantId));
    }

    @PostMapping("/{tenantId}/quotas")
    public ResponseEntity<QuotaScheduleDTO.QuotaScheduleResponse> addQuotaSchedule(
            @PathVariable Long tenantId,
            @Valid @RequestBody QuotaScheduleDTO.QuotaScheduleRequest request) {
        return new ResponseEntity<>(tenantService.addQuotaSchedule(tenantId, request), HttpStatus.CREATED);
    }

    @PutMapping("/{tenantId}/quotas/{scheduleId}")
    public ResponseEntity<QuotaScheduleDTO.QuotaScheduleResponse> updateQuotaSchedule(
            @PathVariable Long tenantId,
            @PathVariable Long scheduleId,
            @Valid @RequestBody QuotaScheduleDTO.QuotaScheduleRequest request) {
        return ResponseEntity.ok(tenantService.updateQuotaSchedule(tenantId, scheduleId, request));
    }

    @DeleteMapping("/{tenantId}/quotas/{scheduleId}")
    public ResponseEntity<Void> deleteQuotaSchedule(@PathVariable Long tenantId, @PathVariable Long scheduleId) {
        tenantService.deleteQuotaSchedule(tenantId, scheduleId);
        return ResponseEntity.noContent().build();
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
