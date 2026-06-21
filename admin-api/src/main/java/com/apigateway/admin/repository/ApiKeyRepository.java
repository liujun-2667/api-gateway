package com.apigateway.admin.repository;

import com.apigateway.common.entity.ApiKey;
import com.apigateway.common.enums.ApiKeyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    Optional<ApiKey> findByKeyId(String keyId);

    Optional<ApiKey> findByApiKey(String apiKey);

    List<ApiKey> findByTenantId(Long tenantId);

    List<ApiKey> findByTenantIdAndStatus(Long tenantId, ApiKeyStatus status);

    @Modifying
    @Query("UPDATE ApiKey k SET k.status = :newStatus WHERE k.status = :oldStatus AND k.updatedAt < :transitionTime")
    int updateStatusAfterTransition(@Param("oldStatus") ApiKeyStatus oldStatus,
                                     @Param("newStatus") ApiKeyStatus newStatus,
                                     @Param("transitionTime") LocalDateTime transitionTime);
}
