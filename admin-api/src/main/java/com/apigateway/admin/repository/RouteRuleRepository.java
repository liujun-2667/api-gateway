package com.apigateway.admin.repository;

import com.apigateway.common.entity.RouteRule;
import com.apigateway.common.enums.RuleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RouteRuleRepository extends JpaRepository<RouteRule, Long> {

    Optional<RouteRule> findByRuleId(String ruleId);

    List<RouteRule> findByApplicationId(Long applicationId);

    List<RouteRule> findByApplicationIdAndStatus(Long applicationId, RuleStatus status);

    List<RouteRule> findByTenantId(Long tenantId);

    boolean existsByRuleId(String ruleId);
}
