package com.apigateway.gateway.repository;

import com.apigateway.gateway.entity.TrafficColorRuleR2dbc;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface TrafficColorRuleRepository extends ReactiveCrudRepository<TrafficColorRuleR2dbc, Long> {

    Flux<TrafficColorRuleR2dbc> findByAppIdOrderByPriorityAsc(Long appId);

    Flux<TrafficColorRuleR2dbc> findByEnabledTrue();
}
