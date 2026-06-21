package com.apigateway.admin.repository;

import com.apigateway.common.entity.TrafficColorRule;
import com.apigateway.common.enums.RuleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrafficColorRuleRepository extends JpaRepository<TrafficColorRule, Long> {

    Optional<TrafficColorRule> findByRuleId(String ruleId);

    List<TrafficColorRule> findByRouteRuleId(Long routeRuleId);

    List<TrafficColorRule> findByTenantId(Long tenantId);

    List<TrafficColorRule> findByTenantIdAndStatus(Long tenantId, RuleStatus status);

    @Modifying
    @Query("UPDATE TrafficColorRule r SET r.status = :status WHERE r.tenantId = :tenantId")
    int updateStatusByTenantId(@Param("tenantId") Long tenantId, @Param("status") RuleStatus status);

    @Modifying
    @Query("DELETE FROM TrafficColorRule r WHERE r.tenantId = :tenantId")
    int deleteByTenantId(@Param("tenantId") Long tenantId);
}
