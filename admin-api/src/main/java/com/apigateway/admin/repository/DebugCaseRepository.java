package com.apigateway.admin.repository;

import com.apigateway.common.entity.DebugCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DebugCaseRepository extends JpaRepository<DebugCase, Long> {

    List<DebugCase> findByEndpointIdOrderByCreatedAtDesc(Long endpointId);
}
