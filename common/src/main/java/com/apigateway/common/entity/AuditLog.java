package com.apigateway.common.entity;

import com.apigateway.common.enums.OperationType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String logId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @Column(nullable = false, length = 128)
    private String resourceType;

    @Column(length = 64)
    private String resourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OperationType operationType;

    @Column(length = 64)
    private String operatorId;

    @Column(length = 128)
    private String operatorName;

    @Column(length = 64)
    private String operatorIp;

    @Column(columnDefinition = "TEXT")
    private String beforeValue;

    @Column(columnDefinition = "TEXT")
    private String afterValue;

    @Column(nullable = false)
    private Boolean success;

    @Column(length = 1024)
    private String errorMessage;

    @Column(length = 2048)
    private String requestUri;

    @Column(length = 16)
    private String requestMethod;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
