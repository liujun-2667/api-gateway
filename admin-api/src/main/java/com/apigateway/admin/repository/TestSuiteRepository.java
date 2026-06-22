package com.apigateway.admin.repository;

import com.apigateway.common.entity.TestSuite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestSuiteRepository extends JpaRepository<TestSuite, Long> {
    List<TestSuite> findByApplicationIdOrderByCreatedAtDesc(Long applicationId);
}
