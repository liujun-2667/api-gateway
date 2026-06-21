package com.apigateway.gateway.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("rate_limit_configs")
public class RateLimitConfigR2dbc {

    @Id
    private Long id;

    @Column("app_id")
    private Long appId;

    @Column("rule_id")
    private Long ruleId;

    @Column("scope")
    private String scope;

    @Column("limit_per_second")
    private Long limitPerSecond;

    @Column("burst_capacity")
    private Long burstCapacity;

    @Column("enabled")
    private Boolean enabled;
}
