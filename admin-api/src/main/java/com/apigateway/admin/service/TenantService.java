package com.apigateway.admin.service;

import com.apigateway.admin.annotation.Auditable;
import com.apigateway.admin.repository.TenantRepository;
import com.apigateway.common.dto.PageResponse;
import com.apigateway.common.dto.QuotaScheduleDTO;
import com.apigateway.common.dto.TenantDTO;
import com.apigateway.common.entity.QuotaSchedule;
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
public class TenantService {

    private final TenantRepository tenantRepository;
    private final QuotaScheduleService quotaScheduleService;

    @Transactional(readOnly = true)
    public PageResponse<TenantDTO.TenantResponse> getAllTenants(Pageable pageable) {
        Page<Tenant> page = tenantRepository.findAll(pageable);
        return toPageResponse(page);
    }

    @Transactional(readOnly = true)
    public TenantDTO.TenantResponse getTenantById(Long id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Tenant", id.toString()));
        return toResponse(tenant);
    }

    @Transactional(readOnly = true)
    public TenantDTO.TenantResponse getTenantByTenantId(String tenantId) {
        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new NotFoundException("Tenant", tenantId));
        return toResponse(tenant);
    }

    @Auditable(resourceType = "Tenant", operationType = OperationType.CREATE)
    @Transactional
    public TenantDTO.TenantResponse createTenant(TenantDTO.TenantRequest request) {
        if (tenantRepository.existsByTenantId(request.getTenantId())) {
            throw new BusinessException("Tenant ID already exists");
        }

        Tenant tenant = Tenant.builder()
                .tenantId(request.getTenantId())
                .name(request.getName())
                .description(request.getDescription())
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .contactEmail(request.getContactEmail())
                .contactPhone(request.getContactPhone())
                .build();

        tenant = tenantRepository.save(tenant);
        return toResponse(tenant);
    }

    @Auditable(resourceType = "Tenant", operationType = OperationType.UPDATE)
    @Transactional
    public TenantDTO.TenantResponse updateTenant(Long id, TenantDTO.TenantRequest request) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Tenant", id.toString()));

        if (!tenant.getTenantId().equals(request.getTenantId())
                && tenantRepository.existsByTenantId(request.getTenantId())) {
            throw new BusinessException("Tenant ID already exists");
        }

        tenant.setTenantId(request.getTenantId());
        tenant.setName(request.getName());
        tenant.setDescription(request.getDescription());
        if (request.getEnabled() != null) {
            tenant.setEnabled(request.getEnabled());
        }
        tenant.setContactEmail(request.getContactEmail());
        tenant.setContactPhone(request.getContactPhone());

        tenant = tenantRepository.save(tenant);
        return toResponse(tenant);
    }

    @Auditable(resourceType = "Tenant", operationType = OperationType.DELETE)
    @Transactional
    public void deleteTenant(Long id) {
        if (!tenantRepository.existsById(id)) {
            throw new NotFoundException("Tenant", id.toString());
        }
        tenantRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<QuotaScheduleDTO.QuotaScheduleResponse> getTenantQuotas(Long tenantId) {
        return quotaScheduleService.getQuotaSchedulesByTenantId(tenantId);
    }

    @Auditable(resourceType = "TenantQuota", operationType = OperationType.CREATE)
    @Transactional
    public QuotaScheduleDTO.QuotaScheduleResponse addQuotaSchedule(Long tenantId, QuotaScheduleDTO.QuotaScheduleRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new NotFoundException("Tenant", tenantId.toString()));
        return quotaScheduleService.createQuotaSchedule(tenantId, request);
    }

    @Auditable(resourceType = "TenantQuota", operationType = OperationType.UPDATE)
    @Transactional
    public QuotaScheduleDTO.QuotaScheduleResponse updateQuotaSchedule(Long tenantId, Long scheduleId, QuotaScheduleDTO.QuotaScheduleRequest request) {
        return quotaScheduleService.updateQuotaSchedule(scheduleId, request);
    }

    @Auditable(resourceType = "TenantQuota", operationType = OperationType.DELETE)
    @Transactional
    public void deleteQuotaSchedule(Long tenantId, Long scheduleId) {
        quotaScheduleService.deleteQuotaSchedule(scheduleId);
    }

    private TenantDTO.TenantResponse toResponse(Tenant tenant) {
        return TenantDTO.TenantResponse.builder()
                .id(tenant.getId())
                .tenantId(tenant.getTenantId())
                .name(tenant.getName())
                .description(tenant.getDescription())
                .enabled(tenant.getEnabled())
                .contactEmail(tenant.getContactEmail())
                .contactPhone(tenant.getContactPhone())
                .createdAt(tenant.getCreatedAt())
                .updatedAt(tenant.getUpdatedAt())
                .build();
    }

    private PageResponse<TenantDTO.TenantResponse> toPageResponse(Page<Tenant> page) {
        List<TenantDTO.TenantResponse> content = page.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return PageResponse.<TenantDTO.TenantResponse>builder()
                .content(content)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }
}
