package com.apigateway.admin.repository;

import com.apigateway.common.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, Long> {

    Optional<Application> findByAppId(String appId);

    List<Application> findByTenantId(Long tenantId);

    boolean existsByAppId(String appId);
}
