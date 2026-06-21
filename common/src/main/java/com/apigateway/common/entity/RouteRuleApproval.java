package com.apigateway.common.entity;

import com.apigateway.common.enums.ApprovalStatus;
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
@Table(name = "route_rule_approvals")
public class RouteRuleApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String approvalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_rule_id", nullable = false)
    private RouteRule routeRule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_rule_version_id", nullable = false)
    private RouteRuleVersion routeRuleVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ApprovalStatus status;

    @Column(length = 64)
    private String approverId;

    @Column(length = 128)
    private String approverName;

    @Column(length = 1024)
    private String approvalComment;

    @Column
    private LocalDateTime approvedAt;

    @Column(length = 64)
    private String requesterId;

    @Column(length = 128)
    private String requesterName;

    @Column(length = 1024)
    private String requestComment;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
