package com.apigateway.gateway.service;

import com.apigateway.gateway.entity.ApiKeyR2dbc;
import com.apigateway.gateway.entity.TenantR2dbc;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantKeyInfo implements Serializable {
    private Long apiKeyId;
    private String keyId;
    private String apiKey;
    private Long tenantId;
    private String tenantName;
    private String tenantCode;
    private Boolean tenantEnabled;
    private Integer tenantMaxQps;
    private Long applicationId;
    private String status;
    private LocalDateTime expiresAt;
    private String allowedIps;
    private Long rateLimitPerSecond;
    private Long rateLimitPerDay;

    public static TenantKeyInfo from(ApiKeyR2dbc apiKey, TenantR2dbc tenant) {
        return TenantKeyInfo.builder()
                .apiKeyId(apiKey.getId())
                .keyId(apiKey.getKeyId())
                .apiKey(apiKey.getApiKey())
                .tenantId(tenant != null ? tenant.getId() : null)
                .tenantName(tenant != null ? tenant.getName() : null)
                .tenantCode(tenant != null ? tenant.getCode() : null)
                .tenantEnabled(tenant != null ? tenant.getEnabled() : false)
                .tenantMaxQps(tenant != null ? tenant.getMaxQps() : null)
                .applicationId(apiKey.getApplicationId())
                .status(apiKey.getStatus())
                .expiresAt(apiKey.getExpiresAt())
                .allowedIps(apiKey.getAllowedIps())
                .rateLimitPerSecond(apiKey.getRateLimitPerSecond())
                .rateLimitPerDay(apiKey.getRateLimitPerDay())
                .build();
    }
}
