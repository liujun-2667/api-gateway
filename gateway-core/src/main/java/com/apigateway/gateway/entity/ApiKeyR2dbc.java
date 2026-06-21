package com.apigateway.gateway.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("api_keys")
public class ApiKeyR2dbc {

    @Id
    private Long id;

    @Column("key_id")
    private String keyId;

    @Column("name")
    private String name;

    @Column("api_key")
    private String apiKey;

    @Column("tenant_id")
    private Long tenantId;

    @Column("application_id")
    private Long applicationId;

    @Column("status")
    private String status;

    @Column("expires_at")
    private LocalDateTime expiresAt;

    @Column("allowed_ips")
    private String allowedIps;

    @Column("rate_limit_per_second")
    private Long rateLimitPerSecond;

    @Column("rate_limit_per_day")
    private Long rateLimitPerDay;

    @Column("created_at")
    private LocalDateTime createdAt;
}
