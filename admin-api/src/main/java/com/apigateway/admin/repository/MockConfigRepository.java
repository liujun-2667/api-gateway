package com.apigateway.admin.repository;

import com.apigateway.common.entity.MockConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MockConfigRepository extends JpaRepository<MockConfig, Long> {

    Optional<MockConfig> findByEndpointId(Long endpointId);

    Optional<MockConfig> findByRouteRuleId(Long routeRuleId);

    boolean existsByMockConfigId(String mockConfigId);
}
