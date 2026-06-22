package com.apigateway.admin.repository;

import com.apigateway.common.entity.ChangeRecordRemark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChangeRecordRemarkRepository extends JpaRepository<ChangeRecordRemark, Long> {
    List<ChangeRecordRemark> findByChangeRecordIdOrderByCreatedAtDesc(Long changeRecordId);
    List<ChangeRecordRemark> findByChangeRecordIdAndFieldPath(Long changeRecordId, String fieldPath);
}
