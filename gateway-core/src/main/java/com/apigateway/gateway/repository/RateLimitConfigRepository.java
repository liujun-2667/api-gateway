package com.apigateway.gateway.repository;

import com.apigateway.common.entity.RateLimitConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RateLimitConfigRepository extends JpaRepository<RateLimitConfig, Long> {

    @Query("SELECT r FROM RateLimitConfig r JOIN FETCH r.tenant WHERE r.enabled = true")
    List<RateLimitConfig> findAllEnabledWithTenant();

    @Query("SELECT r FROM RateLimitConfig r JOIN FETCH r.tenant WHERE r.tenant.id = :tenantId AND r.enabled = true")
    List<RateLimitConfig> findByTenantIdAndEnabledWithTenant(@Param("tenantId") Long tenantId);

    @Query("SELECT r FROM RateLimitConfig r JOIN FETCH r.tenant WHERE r.tenant.id = :tenantId AND r.routeRule.id = :routeRuleId AND r.enabled = true")
    Optional<RateLimitConfig> findByTenantIdAndRouteRuleIdAndEnabled(@Param("tenantId") Long tenantId, @Param("routeRuleId") Long routeRuleId);

    @Query("SELECT r FROM RateLimitConfig r JOIN FETCH r.tenant WHERE r.tenant.id = :tenantId AND r.apiKey.id = :apiKeyId AND r.enabled = true")
    Optional<RateLimitConfig> findByTenantIdAndApiKeyIdAndEnabled(@Param("tenantId") Long tenantId, @Param("apiKeyId") Long apiKeyId);
}
