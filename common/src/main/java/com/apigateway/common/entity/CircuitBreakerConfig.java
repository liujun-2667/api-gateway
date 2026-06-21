package com.apigateway.common.entity;

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
@Table(name = "circuit_breaker_configs")
public class CircuitBreakerConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String configId;

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

    @Column(nullable = false)
    private Float failureRateThreshold;

    @Column(nullable = false)
    private Float slowCallRateThreshold;

    @Column(nullable = false)
    private Long slowCallDurationThreshold;

    @Column(nullable = false)
    private Integer permittedNumberOfCallsInHalfOpenState;

    @Column(nullable = false)
    private Integer slidingWindowSize;

    @Column(nullable = false, length = 32)
    private String slidingWindowType;

    @Column(nullable = false)
    private Integer minimumNumberOfCalls;

    @Column(nullable = false)
    private Long waitDurationInOpenState;

    @Column(length = 1024)
    private String fallbackUrl;

    @Column(nullable = false)
    private Boolean enabled;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
