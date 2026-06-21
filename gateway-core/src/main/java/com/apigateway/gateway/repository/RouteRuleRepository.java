package com.apigateway.gateway.repository;

import com.apigateway.common.entity.RouteRule;
import com.apigateway.common.enums.RuleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RouteRuleRepository extends JpaRepository<RouteRule, Long> {

    @Query("SELECT r FROM RouteRule r JOIN FETCH r.tenant JOIN FETCH r.application WHERE r.status = :status ORDER BY r.priority DESC")
    List<RouteRule> findAllActiveWithTenantAndApplication(RuleStatus status);

    List<RouteRule> findByTenantIdAndStatus(Long tenantId, RuleStatus status);
}
