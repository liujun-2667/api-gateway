package com.apigateway.admin.controller;

import com.apigateway.common.dto.ApplicationDTO;
import com.apigateway.common.dto.PageResponse;
import com.apigateway.admin.service.ApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tenants/{tenantId}/apps")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    @GetMapping
    public ResponseEntity<PageResponse<ApplicationDTO.ApplicationResponse>> getApplications(
            @PathVariable Long tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String[] sort) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(parseSort(sort)));
        return ResponseEntity.ok(applicationService.getApplicationsByTenantId(tenantId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApplicationDTO.ApplicationResponse> getApplicationById(@PathVariable Long tenantId,
                                                                                  @PathVariable Long id) {
        return ResponseEntity.ok(applicationService.getApplicationById(id));
    }

    @PostMapping
    public ResponseEntity<ApplicationDTO.ApplicationResponse> createApplication(
            @PathVariable Long tenantId,
            @Valid @RequestBody ApplicationDTO.ApplicationRequest request) {
        return new ResponseEntity<>(applicationService.createApplication(tenantId, request), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApplicationDTO.ApplicationResponse> updateApplication(
            @PathVariable Long tenantId,
            @PathVariable Long id,
            @Valid @RequestBody ApplicationDTO.ApplicationRequest request) {
        return ResponseEntity.ok(applicationService.updateApplication(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteApplication(@PathVariable Long tenantId, @PathVariable Long id) {
        applicationService.deleteApplication(id);
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
