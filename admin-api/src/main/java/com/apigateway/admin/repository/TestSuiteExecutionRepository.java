package com.apigateway.admin.repository;

import com.apigateway.common.entity.TestSuiteExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestSuiteExecutionRepository extends JpaRepository<TestSuiteExecution, Long> {
    List<TestSuiteExecution> findByTestSuiteIdOrderByCreatedAtDesc(Long testSuiteId);
}
