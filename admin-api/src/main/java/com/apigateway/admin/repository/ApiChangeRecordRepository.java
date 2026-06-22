package com.apigateway.admin.repository;

import com.apigateway.common.entity.ApiChangeRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApiChangeRecordRepository extends JpaRepository<ApiChangeRecord, Long> {

    List<ApiChangeRecord> findByEndpointIdOrderByCreatedAtDesc(Long endpointId);

    List<ApiChangeRecord> findByEndpointIdInOrderByCreatedAtDesc(List<Long> endpointIds);
}
