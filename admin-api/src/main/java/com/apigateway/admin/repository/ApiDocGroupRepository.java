package com.apigateway.admin.repository;

import com.apigateway.common.entity.ApiDocGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApiDocGroupRepository extends JpaRepository<ApiDocGroup, Long> {

    List<ApiDocGroup> findByApiDocIdOrderBySortOrder(Long docId);

    List<ApiDocGroup> findByApiDocId(Long docId);
}
