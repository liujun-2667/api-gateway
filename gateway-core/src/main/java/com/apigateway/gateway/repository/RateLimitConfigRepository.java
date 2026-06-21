package com.apigateway.gateway.repository;

import com.apigateway.gateway.entity.RateLimitConfigR2dbc;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface RateLimitConfigRepository extends ReactiveCrudRepository<RateLimitConfigR2dbc, Long> {

    Flux<RateLimitConfigR2dbc> findByAppId(Long appId);
}
