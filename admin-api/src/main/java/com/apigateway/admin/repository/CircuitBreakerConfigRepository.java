package com.apigateway.admin.repository;

import com.apigateway.common.entity.CircuitBreakerConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CircuitBreakerConfigRepository extends JpaRepository<CircuitBreakerConfig, Long> {

    Optional<CircuitBreakerConfig> findByConfigId(String configId);

    List<CircuitBreakerConfig> findByTenantId(Long tenantId);

    List<CircuitBreakerConfig> findByRouteRuleId(Long routeRuleId);

    boolean existsByConfigId(String configId);
}
