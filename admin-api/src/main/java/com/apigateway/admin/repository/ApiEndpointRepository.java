package com.apigateway.admin.repository;

import com.apigateway.common.entity.ApiEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApiEndpointRepository extends JpaRepository<ApiEndpoint, Long> {

    List<ApiEndpoint> findByGroupIdOrderBySortOrder(Long groupId);

    List<ApiEndpoint> findByGroupId(Long groupId);
}
