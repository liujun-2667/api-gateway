package com.apigateway.common.entity;

import com.apigateway.common.enums.HttpMethod;
import com.apigateway.common.enums.RuleStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "route_rule_versions")
public class RouteRuleVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String versionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_rule_id", nullable = false)
    private RouteRule routeRule;

    @Column(nullable = false)
    private Integer version;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 512)
    private String description;

    @Column(nullable = false, length = 512)
    private String path;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private HttpMethod method;

    @Column(length = 512)
    private String targetUrl;

    @Column(nullable = false)
    private Integer priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RuleStatus status;

    @Column(nullable = false)
    private Boolean requiresAuth;

    @Column(nullable = false)
    private Boolean rateLimitEnabled;

    @Column(nullable = false)
    private Boolean circuitBreakerEnabled;

    @Column(columnDefinition = "TEXT")
    private String configSnapshot;

    @Column(length = 1024)
    private String changeLog;

    @Column(length = 64)
    private String createdBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<TargetBackend> targetBackends;

    @Column
    private Integer connectTimeoutMs;

    @Column
    private Integer readTimeoutMs;

    @Column
    private Integer maxRetries;

    @Column
    private Boolean retryOn5xx;

    @Column
    private Boolean retryOnTimeout;

    @Column
    private Integer retryIntervalMs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, String> requestHeadersToAdd;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<String> requestHeadersToRemove;

    @Column(length = 512)
    private String pathPrefixReplacement;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
