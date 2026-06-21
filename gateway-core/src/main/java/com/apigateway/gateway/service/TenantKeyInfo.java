package com.apigateway.gateway.service;

import com.apigateway.common.entity.Tenant;
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
    private Boolean tenantEnabled;
    private Long applicationId;
    private String status;
    private LocalDateTime expiresAt;
    private String allowedIps;
    private Long rateLimitPerSecond;
    private Long rateLimitPerDay;
}
