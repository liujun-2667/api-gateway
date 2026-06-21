package com.apigateway.gateway.repository;

import com.apigateway.common.entity.CircuitBreakerConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CircuitBreakerConfigRepository extends JpaRepository<CircuitBreakerConfig, Long> {

    @Query("SELECT c FROM CircuitBreakerConfig c JOIN FETCH c.tenant WHERE c.enabled = true")
    List<CircuitBreakerConfig> findAllEnabledWithTenant();

    @Query("SELECT c FROM CircuitBreakerConfig c JOIN FETCH c.tenant WHERE c.tenant.id = :tenantId AND c.enabled = true")
    List<CircuitBreakerConfig> findByTenantIdAndEnabledWithTenant(@Param("tenantId") Long tenantId);

    @Query("SELECT c FROM CircuitBreakerConfig c JOIN FETCH c.tenant WHERE c.tenant.id = :tenantId AND c.routeRule.id = :routeRuleId AND c.enabled = true")
    Optional<CircuitBreakerConfig> findByTenantIdAndRouteRuleIdAndEnabled(@Param("tenantId") Long tenantId, @Param("routeRuleId") Long routeRuleId);
}
