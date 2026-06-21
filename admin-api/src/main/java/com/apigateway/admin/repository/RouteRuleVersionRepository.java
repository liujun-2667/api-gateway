package com.apigateway.admin.repository;

import com.apigateway.common.entity.RouteRuleVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RouteRuleVersionRepository extends JpaRepository<RouteRuleVersion, Long> {

    Optional<RouteRuleVersion> findByVersionId(String versionId);

    List<RouteRuleVersion> findByRouteRuleIdOrderByVersionDesc(Long routeRuleId);

    Optional<RouteRuleVersion> findFirstByRouteRuleIdOrderByVersionDesc(Long routeRuleId);

    Optional<RouteRuleVersion> findByRouteRuleIdAndVersion(Long routeRuleId, Integer version);
}
