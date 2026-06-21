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
@Table("route_rules")
public class RouteRuleR2dbc {

    @Id
    private Long id;

    @Column("app_id")
    private Long appId;

    @Column("name")
    private String name;

    @Column("path_prefix")
    private String pathPrefix;

    @Column("match_type")
    private String matchType;

    @Column("http_method")
    private String httpMethod;

    @Column("priority")
    private Integer priority;

    @Column("target_backends")
    private String targetBackends;

    @Column("connect_timeout_ms")
    private Integer connectTimeoutMs;

    @Column("read_timeout_ms")
    private Integer readTimeoutMs;

    @Column("max_retries")
    private Integer maxRetries;

    @Column("retry_on_5xx")
    private Boolean retryOn5xx;

    @Column("retry_on_timeout")
    private Boolean retryOnTimeout;

    @Column("retry_interval_ms")
    private Integer retryIntervalMs;

    @Column("request_headers_to_add")
    private String requestHeadersToAdd;

    @Column("request_headers_to_remove")
    private String requestHeadersToRemove;

    @Column("path_prefix_replacement")
    private String pathPrefixReplacement;

    @Column("status")
    private String status;

    @Column("version")
    private Integer version;

    @Column("published_at")
    private LocalDateTime publishedAt;
}
