package com.apigateway.admin.service;

import com.apigateway.admin.annotation.Auditable;
import com.apigateway.admin.repository.ApplicationRepository;
import com.apigateway.admin.repository.TenantRepository;
import com.apigateway.common.dto.ApplicationDTO;
import com.apigateway.common.dto.PageResponse;
import com.apigateway.common.entity.Application;
import com.apigateway.common.entity.Tenant;
import com.apigateway.common.enums.OperationType;
import com.apigateway.common.exception.BusinessException;
import com.apigateway.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final TenantRepository tenantRepository;

    @Transactional(readOnly = true)
    public PageResponse<ApplicationDTO.ApplicationResponse> getApplicationsByTenantId(Long tenantId, Pageable pageable) {
        if (!tenantRepository.existsById(tenantId)) {
            throw new NotFoundException("Tenant", tenantId.toString());
        }
        List<Application> apps = applicationRepository.findByTenantId(tenantId);
        return PageResponse.<ApplicationDTO.ApplicationResponse>builder()
                .content(apps.stream().map(this::toResponse).collect(Collectors.toList()))
                .pageNumber(0)
                .pageSize(apps.size())
                .totalElements(apps.size())
                .totalPages(1)
                .first(true)
                .last(true)
                .build();
    }

    @Transactional(readOnly = true)
    public ApplicationDTO.ApplicationResponse getApplicationById(Long id) {
        Application app = applicationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Application", id.toString()));
        return toResponse(app);
    }

    @Auditable(resourceType = "Application", operationType = OperationType.CREATE)
    @Transactional
    public ApplicationDTO.ApplicationResponse createApplication(Long tenantId, ApplicationDTO.ApplicationRequest request) {
        if (applicationRepository.existsByAppId(request.getAppId())) {
            throw new BusinessException("Application ID already exists");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new NotFoundException("Tenant", tenantId.toString()));

        Application app = Application.builder()
                .appId(request.getAppId())
                .name(request.getName())
                .description(request.getDescription())
                .tenant(tenant)
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .baseUrl(request.getBaseUrl())
                .build();

        app = applicationRepository.save(app);
        return toResponse(app);
    }

    @Auditable(resourceType = "Application", operationType = OperationType.UPDATE)
    @Transactional
    public ApplicationDTO.ApplicationResponse updateApplication(Long id, ApplicationDTO.ApplicationRequest request) {
        Application app = applicationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Application", id.toString()));

        if (!app.getAppId().equals(request.getAppId())
                && applicationRepository.existsByAppId(request.getAppId())) {
            throw new BusinessException("Application ID already exists");
        }

        app.setAppId(request.getAppId());
        app.setName(request.getName());
        app.setDescription(request.getDescription());
        if (request.getEnabled() != null) {
            app.setEnabled(request.getEnabled());
        }
        app.setBaseUrl(request.getBaseUrl());

        app = applicationRepository.save(app);
        return toResponse(app);
    }

    @Auditable(resourceType = "Application", operationType = OperationType.DELETE)
    @Transactional
    public void deleteApplication(Long id) {
        if (!applicationRepository.existsById(id)) {
            throw new NotFoundException("Application", id.toString());
        }
        applicationRepository.deleteById(id);
    }

    private ApplicationDTO.ApplicationResponse toResponse(Application app) {
        return ApplicationDTO.ApplicationResponse.builder()
                .id(app.getId())
                .appId(app.getAppId())
                .name(app.getName())
                .description(app.getDescription())
                .tenantId(app.getTenant().getId())
                .tenantName(app.getTenant().getName())
                .enabled(app.getEnabled())
                .baseUrl(app.getBaseUrl())
                .createdAt(app.getCreatedAt())
                .updatedAt(app.getUpdatedAt())
                .build();
    }
}
