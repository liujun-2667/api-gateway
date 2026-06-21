package com.apigateway.gateway.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("circuit_breaker_configs")
public class CircuitBreakerConfigR2dbc {

    @Id
    private Long id;

    @Column("app_id")
    private Long appId;

    @Column("upstream_service")
    private String upstreamService;

    @Column("failure_rate_threshold")
    private Float failureRateThreshold;

    @Column("sliding_window_size")
    private Integer slidingWindowSize;

    @Column("minimum_number_of_calls")
    private Integer minimumNumberOfCalls;

    @Column("wait_duration_in_open_state_ms")
    private Long waitDurationInOpenStateMs;

    @Column("permitted_number_of_calls_in_half_open_state")
    private Integer permittedNumberOfCallsInHalfOpenState;

    @Column("fallback_response_body")
    private String fallbackResponseBody;

    @Column("enabled")
    private Boolean enabled;
}
