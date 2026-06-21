package com.apigateway.gateway.repository;

import com.apigateway.common.entity.ApiKey;
import com.apigateway.common.enums.ApiKeyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    @Query("SELECT ak FROM ApiKey ak JOIN FETCH ak.tenant WHERE ak.apiKey = :apiKey AND ak.status = :status")
    Optional<ApiKey> findByApiKeyAndStatusWithTenant(@Param("apiKey") String apiKey, @Param("status") ApiKeyStatus status);

    Optional<ApiKey> findByKeyId(String keyId);

    Optional<ApiKey> findByApiKey(String apiKey);
}
