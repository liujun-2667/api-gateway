package com.apigateway.gateway.repository;

import com.apigateway.gateway.entity.TenantR2dbc;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface TenantRepository extends ReactiveCrudRepository<TenantR2dbc, Long> {

    Mono<TenantR2dbc> findByCode(String code);
}
