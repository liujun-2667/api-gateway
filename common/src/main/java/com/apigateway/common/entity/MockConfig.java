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
@Table(name = "mock_configs")
public class MockConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String mockConfigId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "endpoint_id", nullable = false)
    private ApiEndpoint endpoint;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_rule_id")
    private RouteRule routeRule;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(nullable = false)
    private Integer delayMs;

    @Column
    private Integer faultInjectionPercent;

    @Column(length = 16)
    private String faultErrorCode;

    @Column(length = 64)
    private String createdBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
