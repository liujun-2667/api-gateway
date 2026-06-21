package com.apigateway.common.entity;

import com.apigateway.common.enums.ColorTagOperation;
import com.apigateway.common.enums.MatchType;
import com.apigateway.common.enums.RuleStatus;
import com.apigateway.common.enums.TrafficConditionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "traffic_color_rules")
public class TrafficColorRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String ruleId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 512)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_rule_id")
    private RouteRule routeRule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TrafficConditionType conditionType;

    @Column(nullable = false, length = 128)
    private String conditionKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MatchType matchType;

    @Column(nullable = false, length = 512)
    private String conditionValue;

    @Column(nullable = false, length = 64)
    private String colorTag;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ColorTagOperation operation;

    @Column(nullable = false)
    private Integer priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RuleStatus status;

    @Column
    private LocalDateTime effectiveFrom;

    @Column
    private LocalDateTime effectiveTo;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
