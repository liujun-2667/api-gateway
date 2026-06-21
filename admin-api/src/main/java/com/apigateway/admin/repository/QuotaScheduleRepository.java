package com.apigateway.admin.repository;

import com.apigateway.common.entity.QuotaSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuotaScheduleRepository extends JpaRepository<QuotaSchedule, Long> {

    Optional<QuotaSchedule> findByScheduleId(String scheduleId);

    List<QuotaSchedule> findByTenantId(Long tenantId);

    List<QuotaSchedule> findByApiKeyId(Long apiKeyId);

    List<QuotaSchedule> findByRateLimitConfigId(Long rateLimitConfigId);

    boolean existsByScheduleId(String scheduleId);
}
