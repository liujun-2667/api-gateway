package com.apigateway.admin.service;

import com.apigateway.admin.annotation.Auditable;
import com.apigateway.admin.repository.ApiKeyRepository;
import com.apigateway.admin.repository.ApplicationRepository;
import com.apigateway.admin.repository.TenantRepository;
import com.apigateway.common.dto.ApiKeyDTO;
import com.apigateway.common.entity.ApiKey;
import com.apigateway.common.entity.Application;
import com.apigateway.common.entity.Tenant;
import com.apigateway.common.enums.ApiKeyStatus;
import com.apigateway.common.enums.OperationType;
import com.apigateway.common.exception.BusinessException;
import com.apigateway.common.exception.NotFoundException;
import com.apigateway.common.utils.ApiKeyGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final TenantRepository tenantRepository;
    private final ApplicationRepository applicationRepository;

    private static final long ROTATION_TRANSITION_HOURS = 24;

    @Transactional(readOnly = true)
    public List<ApiKeyDTO.ApiKeyResponse> getApiKeysByTenantId(Long tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            throw new NotFoundException("Tenant", tenantId.toString());
        }
        return apiKeyRepository.findByTenantId(tenantId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ApiKeyDTO.ApiKeyResponse getApiKeyById(Long id) {
        ApiKey apiKey = apiKeyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("ApiKey", id.toString()));
        return toResponse(apiKey);
    }

    @Auditable(resourceType = "ApiKey", operationType = OperationType.CREATE)
    @Transactional
    public ApiKeyDTO.ApiKeyResponse createApiKey(Long tenantId, ApiKeyDTO.ApiKeyRequest request, String createdBy) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new NotFoundException("Tenant", tenantId.toString()));

        Application application = null;
        if (request.getApplicationId() != null) {
            application = applicationRepository.findById(request.getApplicationId())
                    .orElseThrow(() -> new NotFoundException("Application", request.getApplicationId().toString()));
        }

        String generatedApiKey = ApiKeyGenerator.generateApiKey();
        String keyId = request.getKeyId() != null ? request.getKeyId() : ApiKeyGenerator.generateKeyId();

        ApiKey apiKey = ApiKey.builder()
                .keyId(keyId)
                .name(request.getName())
                .apiKey(generatedApiKey)
                .tenant(tenant)
                .application(application)
                .status(request.getStatus() != null ? request.getStatus() : ApiKeyStatus.ACTIVE)
                .expiresAt(request.getExpiresAt())
                .allowedIps(request.getAllowedIps())
                .rateLimitPerSecond(request.getRateLimitPerSecond())
                .rateLimitPerDay(request.getRateLimitPerDay())
                .createdBy(createdBy)
                .build();

        apiKey = apiKeyRepository.save(apiKey);
        return toResponse(apiKey);
    }

    @Auditable(resourceType = "ApiKey", operationType = OperationType.UPDATE)
    @Transactional
    public ApiKeyDTO.ApiKeyResponse updateApiKey(Long id, ApiKeyDTO.ApiKeyRequest request) {
        ApiKey apiKey = apiKeyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("ApiKey", id.toString()));

        if (request.getApplicationId() != null) {
            Application application = applicationRepository.findById(request.getApplicationId())
                    .orElseThrow(() -> new NotFoundException("Application", request.getApplicationId().toString()));
            apiKey.setApplication(application);
        }

        apiKey.setName(request.getName());
        if (request.getStatus() != null) {
            apiKey.setStatus(request.getStatus());
        }
        apiKey.setExpiresAt(request.getExpiresAt());
        apiKey.setAllowedIps(request.getAllowedIps());
        apiKey.setRateLimitPerSecond(request.getRateLimitPerSecond());
        apiKey.setRateLimitPerDay(request.getRateLimitPerDay());

        apiKey = apiKeyRepository.save(apiKey);
        return toResponse(apiKey);
    }

    @Auditable(resourceType = "ApiKey", operationType = OperationType.ACTIVATE)
    @Transactional
    public ApiKeyDTO.ApiKeyResponse enableApiKey(Long id) {
        ApiKey apiKey = apiKeyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("ApiKey", id.toString()));
        apiKey.setStatus(ApiKeyStatus.ACTIVE);
        apiKey = apiKeyRepository.save(apiKey);
        return toResponse(apiKey);
    }

    @Auditable(resourceType = "ApiKey", operationType = OperationType.DEACTIVATE)
    @Transactional
    public ApiKeyDTO.ApiKeyResponse disableApiKey(Long id) {
        ApiKey apiKey = apiKeyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("ApiKey", id.toString()));
        apiKey.setStatus(ApiKeyStatus.INACTIVE);
        apiKey = apiKeyRepository.save(apiKey);
        return toResponse(apiKey);
    }

    @Auditable(resourceType = "ApiKey", operationType = OperationType.UPDATE)
    @Transactional
    public ApiKeyDTO.ApiKeyResponse rotateApiKey(Long id, String createdBy) {
        ApiKey oldKey = apiKeyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("ApiKey", id.toString()));

        String newKeyValue = ApiKeyGenerator.generateApiKey();

        ApiKey newKey = ApiKey.builder()
                .keyId(ApiKeyGenerator.generateKeyId())
                .name(oldKey.getName() + " (rotated)")
                .apiKey(newKeyValue)
                .tenant(oldKey.getTenant())
                .application(oldKey.getApplication())
                .status(ApiKeyStatus.ACTIVE)
                .expiresAt(oldKey.getExpiresAt())
                .allowedIps(oldKey.getAllowedIps())
                .rateLimitPerSecond(oldKey.getRateLimitPerSecond())
                .rateLimitPerDay(oldKey.getRateLimitPerDay())
                .createdBy(createdBy)
                .build();

        oldKey.setStatus(ApiKeyStatus.INACTIVE);
        oldKey.setUpdatedAt(LocalDateTime.now());
        apiKeyRepository.save(oldKey);

        newKey = apiKeyRepository.save(newKey);
        return toResponse(newKey);
    }

    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void processKeyRotations() {
        LocalDateTime transitionTime = LocalDateTime.now().minusHours(ROTATION_TRANSITION_HOURS);
        apiKeyRepository.updateStatusAfterTransition(
                ApiKeyStatus.INACTIVE,
                ApiKeyStatus.REVOKED,
                transitionTime
        );
    }

    @Auditable(resourceType = "ApiKey", operationType = OperationType.DELETE)
    @Transactional
    public void deleteApiKey(Long id) {
        if (!apiKeyRepository.existsById(id)) {
            throw new NotFoundException("ApiKey", id.toString());
        }
        apiKeyRepository.deleteById(id);
    }

    private ApiKeyDTO.ApiKeyResponse toResponse(ApiKey apiKey) {
        return ApiKeyDTO.ApiKeyResponse.builder()
                .id(apiKey.getId())
                .keyId(apiKey.getKeyId())
                .name(apiKey.getName())
                .apiKey(apiKey.getApiKey())
                .tenantId(apiKey.getTenant() != null ? apiKey.getTenant().getId() : null)
                .tenantName(apiKey.getTenant() != null ? apiKey.getTenant().getName() : null)
                .applicationId(apiKey.getApplication() != null ? apiKey.getApplication().getId() : null)
                .applicationName(apiKey.getApplication() != null ? apiKey.getApplication().getName() : null)
                .status(apiKey.getStatus())
                .expiresAt(apiKey.getExpiresAt())
                .allowedIps(apiKey.getAllowedIps())
                .rateLimitPerSecond(apiKey.getRateLimitPerSecond())
                .rateLimitPerDay(apiKey.getRateLimitPerDay())
                .createdBy(apiKey.getCreatedBy())
                .createdAt(apiKey.getCreatedAt())
                .updatedAt(apiKey.getUpdatedAt())
                .build();
    }
}
