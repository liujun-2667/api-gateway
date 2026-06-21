package com.apigateway.gateway.repository;

import com.apigateway.common.entity.TrafficColorRule;
import com.apigateway.common.enums.RuleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrafficColorRuleRepository extends JpaRepository<TrafficColorRule, Long> {

    @Query("SELECT t FROM TrafficColorRule t JOIN FETCH t.tenant WHERE t.status = :status ORDER BY t.priority DESC")
    List<TrafficColorRule> findAllActiveWithTenant(RuleStatus status);

    @Query("SELECT t FROM TrafficColorRule t JOIN FETCH t.tenant WHERE t.tenant.id = :tenantId AND t.status = :status ORDER BY t.priority DESC")
    List<TrafficColorRule> findByTenantIdAndStatusWithTenant(@Param("tenantId") Long tenantId, @Param("status") RuleStatus status);
}
