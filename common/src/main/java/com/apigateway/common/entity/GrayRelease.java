package com.apigateway.common.entity;

import com.apigateway.common.enums.GrayReleasePhase;
import com.apigateway.common.enums.GrayReleaseStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "gray_releases")
public class GrayRelease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String grayReleaseId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 512)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_rule_id", nullable = false)
    private RouteRule routeRule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "color_rule_id")
    private TrafficColorRule colorRule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GrayReleaseStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private GrayReleasePhase currentPhase;

    @Column
    private Integer currentTrafficPercent;

    @Column(nullable = false)
    private Integer initialPercent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private String releaseStagesJson;

    @Column(nullable = false)
    private Integer observationMinutesPerStage;

    @Column(nullable = false)
    @Builder.Default
    private Double errorRateThreshold = 5.0;

    @Column
    private Double currentErrorRate;

    @Column
    private LocalDateTime phaseStartTime;

    @Column
    private LocalDateTime nextStageTime;

    @Column
    private Integer totalStages;

    @Column
    private Integer completedStages;

    @Column(length = 64)
    private String createdBy;

    @Column(length = 64)
    private String updatedBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
