package com.apigateway.gateway.repository;

import com.apigateway.gateway.entity.RouteRuleR2dbc;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface RouteRuleRepository extends ReactiveCrudRepository<RouteRuleR2dbc, Long> {

    Flux<RouteRuleR2dbc> findByAppIdOrderByPriorityAsc(Long appId);

    Flux<RouteRuleR2dbc> findByStatus(String status);
}
