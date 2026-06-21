package com.apigateway.admin.service;

import com.apigateway.admin.repository.ApiKeyRepository;
import com.apigateway.admin.repository.QuotaScheduleRepository;
import com.apigateway.admin.repository.RateLimitConfigRepository;
import com.apigateway.admin.repository.TenantRepository;
import com.apigateway.common.dto.QuotaScheduleDTO;
import com.apigateway.common.entity.ApiKey;
import com.apigateway.common.entity.QuotaSchedule;
import com.apigateway.common.entity.RateLimitConfig;
import com.apigateway.common.entity.Tenant;
import com.apigateway.common.exception.BusinessException;
import com.apigateway.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuotaScheduleService {

    private final QuotaScheduleRepository quotaScheduleRepository;
    private final TenantRepository tenantRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final RateLimitConfigRepository rateLimitConfigRepository;

    @Transactional(readOnly = true)
    public List<QuotaScheduleDTO.QuotaScheduleResponse> getQuotaSchedulesByTenantId(Long tenantId) {
        return quotaScheduleRepository.findByTenantId(tenantId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public QuotaScheduleDTO.QuotaScheduleResponse getQuotaScheduleById(Long id) {
        QuotaSchedule schedule = quotaScheduleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("QuotaSchedule", id.toString()));
        return toResponse(schedule);
    }

    @Transactional
    public QuotaScheduleDTO.QuotaScheduleResponse createQuotaSchedule(Long tenantId, QuotaScheduleDTO.QuotaScheduleRequest request) {
        if (quotaScheduleRepository.existsByScheduleId(request.getScheduleId())) {
            throw new BusinessException("Schedule ID already exists");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new NotFoundException("Tenant", tenantId.toString()));

        ApiKey apiKey = null;
        if (request.getApiKeyId() != null) {
            apiKey = apiKeyRepository.findById(request.getApiKeyId())
                    .orElseThrow(() -> new NotFoundException("ApiKey", request.getApiKeyId().toString()));
        }

        RateLimitConfig rateLimitConfig = null;
        if (request.getRateLimitConfigId() != null) {
            rateLimitConfig = rateLimitConfigRepository.findById(request.getRateLimitConfigId())
                    .orElseThrow(() -> new NotFoundException("RateLimitConfig", request.getRateLimitConfigId().toString()));
        }

        QuotaSchedule schedule = QuotaSchedule.builder()
                .scheduleId(request.getScheduleId())
                .name(request.getName())
                .description(request.getDescription())
                .tenant(tenant)
                .apiKey(apiKey)
                .rateLimitConfig(rateLimitConfig)
                .scheduleType(request.getScheduleType())
                .daysOfWeek(request.getDaysOfWeek())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .adjustedRequestsPerSecond(request.getAdjustedRequestsPerSecond())
                .adjustedRequestsPerMinute(request.getAdjustedRequestsPerMinute())
                .adjustedRequestsPerHour(request.getAdjustedRequestsPerHour())
                .adjustedRequestsPerDay(request.getAdjustedRequestsPerDay())
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .effectiveFrom(request.getEffectiveFrom())
                .effectiveTo(request.getEffectiveTo())
                .build();

        schedule = quotaScheduleRepository.save(schedule);
        return toResponse(schedule);
    }

    @Transactional
    public QuotaScheduleDTO.QuotaScheduleResponse updateQuotaSchedule(Long id, QuotaScheduleDTO.QuotaScheduleRequest request) {
        QuotaSchedule schedule = quotaScheduleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("QuotaSchedule", id.toString()));

        schedule.setName(request.getName());
        schedule.setDescription(request.getDescription());
        schedule.setScheduleType(request.getScheduleType());
        schedule.setDaysOfWeek(request.getDaysOfWeek());
        schedule.setStartTime(request.getStartTime());
        schedule.setEndTime(request.getEndTime());
        schedule.setAdjustedRequestsPerSecond(request.getAdjustedRequestsPerSecond());
        schedule.setAdjustedRequestsPerMinute(request.getAdjustedRequestsPerMinute());
        schedule.setAdjustedRequestsPerHour(request.getAdjustedRequestsPerHour());
        schedule.setAdjustedRequestsPerDay(request.getAdjustedRequestsPerDay());
        if (request.getEnabled() != null) {
            schedule.setEnabled(request.getEnabled());
        }
        schedule.setEffectiveFrom(request.getEffectiveFrom());
        schedule.setEffectiveTo(request.getEffectiveTo());

        schedule = quotaScheduleRepository.save(schedule);
        return toResponse(schedule);
    }

    @Transactional
    public void deleteQuotaSchedule(Long id) {
        if (!quotaScheduleRepository.existsById(id)) {
            throw new NotFoundException("QuotaSchedule", id.toString());
        }
        quotaScheduleRepository.deleteById(id);
    }

    private QuotaScheduleDTO.QuotaScheduleResponse toResponse(QuotaSchedule schedule) {
        return QuotaScheduleDTO.QuotaScheduleResponse.builder()
                .id(schedule.getId())
                .scheduleId(schedule.getScheduleId())
                .name(schedule.getName())
                .description(schedule.getDescription())
                .tenantId(schedule.getTenant() != null ? schedule.getTenant().getId() : null)
                .tenantName(schedule.getTenant() != null ? schedule.getTenant().getName() : null)
                .apiKeyId(schedule.getApiKey() != null ? schedule.getApiKey().getId() : null)
                .apiKeyName(schedule.getApiKey() != null ? schedule.getApiKey().getName() : null)
                .rateLimitConfigId(schedule.getRateLimitConfig() != null ? schedule.getRateLimitConfig().getId() : null)
                .rateLimitConfigName(schedule.getRateLimitConfig() != null ? schedule.getRateLimitConfig().getName() : null)
                .scheduleType(schedule.getScheduleType())
                .daysOfWeek(schedule.getDaysOfWeek())
                .startTime(schedule.getStartTime())
                .endTime(schedule.getEndTime())
                .adjustedRequestsPerSecond(schedule.getAdjustedRequestsPerSecond())
                .adjustedRequestsPerMinute(schedule.getAdjustedRequestsPerMinute())
                .adjustedRequestsPerHour(schedule.getAdjustedRequestsPerHour())
                .adjustedRequestsPerDay(schedule.getAdjustedRequestsPerDay())
                .enabled(schedule.getEnabled())
                .effectiveFrom(schedule.getEffectiveFrom())
                .effectiveTo(schedule.getEffectiveTo())
                .createdAt(schedule.getCreatedAt())
                .updatedAt(schedule.getUpdatedAt())
                .build();
    }
}
