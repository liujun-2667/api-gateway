package com.apigateway.gateway.repository;

import com.apigateway.gateway.entity.CircuitBreakerConfigR2dbc;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface CircuitBreakerConfigRepository extends ReactiveCrudRepository<CircuitBreakerConfigR2dbc, Long> {

    Flux<CircuitBreakerConfigR2dbc> findByAppId(Long appId);
}
