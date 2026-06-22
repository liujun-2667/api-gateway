package com.apigateway.admin.repository;

import com.apigateway.common.entity.GrayRelease;
import com.apigateway.common.enums.GrayReleaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GrayReleaseRepository extends JpaRepository<GrayRelease, Long> {

    Optional<GrayRelease> findByGrayReleaseId(String grayReleaseId);

    List<GrayRelease> findByApplicationIdOrderByCreatedAtDesc(Long applicationId);

    List<GrayRelease> findByStatusIn(List<GrayReleaseStatus> statuses);

    Optional<GrayRelease> findByRouteRuleIdAndStatusIn(Long routeRuleId, List<GrayReleaseStatus> statuses);

    boolean existsByRouteRuleIdAndStatusIn(Long routeRuleId, List<GrayReleaseStatus> statuses);
}
