package com.apigateway.admin.repository;

import com.apigateway.common.entity.ApiDoc;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApiDocRepository extends JpaRepository<ApiDoc, Long> {

    List<ApiDoc> findByApplicationIdOrderByCreatedAtDesc(Long applicationId);

    boolean existsByDocId(String docId);
}
