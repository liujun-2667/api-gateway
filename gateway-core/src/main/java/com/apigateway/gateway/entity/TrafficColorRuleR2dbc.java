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
@Table("traffic_color_rules")
public class TrafficColorRuleR2dbc {

    @Id
    private Long id;

    @Column("app_id")
    private Long appId;

    @Column("name")
    private String name;

    @Column("priority")
    private Integer priority;

    @Column("tag_value")
    private String tagValue;

    @Column("condition_type")
    private String conditionType;

    @Column("condition_value")
    private String conditionValue;

    @Column("enabled")
    private Boolean enabled;

    @Column("operation_type")
    private String operationType;
}
