package com.apigateway.admin.repository;

import com.apigateway.common.entity.RateLimitConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RateLimitConfigRepository extends JpaRepository<RateLimitConfig, Long> {

    Optional<RateLimitConfig> findByConfigId(String configId);

    List<RateLimitConfig> findByTenantId(Long tenantId);

    List<RateLimitConfig> findByRouteRuleId(Long routeRuleId);

    List<RateLimitConfig> findByApiKeyId(Long apiKeyId);

    boolean existsByConfigId(String configId);
}
