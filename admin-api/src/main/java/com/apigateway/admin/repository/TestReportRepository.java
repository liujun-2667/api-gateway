package com.apigateway.admin.repository;

import com.apigateway.common.entity.TestReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestReportRepository extends JpaRepository<TestReport, Long> {
    List<TestReport> findByTestSuiteIdOrderByCreatedAtDesc(Long testSuiteId);
}
