package com.apigateway.admin.repository;

import com.apigateway.common.entity.RouteRuleApproval;
import com.apigateway.common.enums.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RouteRuleApprovalRepository extends JpaRepository<RouteRuleApproval, Long> {

    Optional<RouteRuleApproval> findByApprovalId(String approvalId);

    List<RouteRuleApproval> findByRouteRuleIdOrderByCreatedAtDesc(Long routeRuleId);

    List<RouteRuleApproval> findByStatus(ApprovalStatus status);

    Optional<RouteRuleApproval> findFirstByRouteRuleIdOrderByCreatedAtDesc(Long routeRuleId);
}
