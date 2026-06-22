-- PostgreSQL 数据库初始化脚本
-- API Gateway 数据库表结构和初始数据

-- ============================================
-- 表创建
-- ============================================

-- 租户表
CREATE TABLE IF NOT EXISTS tenants (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    code VARCHAR(64) NOT NULL UNIQUE,
    description VARCHAR(512),
    max_qps INTEGER NOT NULL DEFAULT 100,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 应用表
CREATE TABLE IF NOT EXISTS applications (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(128) NOT NULL,
    code VARCHAR(64) NOT NULL,
    description VARCHAR(512),
    base_path VARCHAR(256),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, code)
);

-- 管理员用户表
CREATE TABLE IF NOT EXISTS admin_users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(256) NOT NULL,
    email VARCHAR(128),
    role VARCHAR(32) NOT NULL,
    tenant_id BIGINT REFERENCES tenants(id) ON DELETE SET NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- API密钥表
CREATE TABLE IF NOT EXISTS api_keys (
    id BIGSERIAL PRIMARY KEY,
    key_id VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    api_key VARCHAR(256) NOT NULL UNIQUE,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    application_id BIGINT REFERENCES applications(id) ON DELETE SET NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    expires_at TIMESTAMP,
    rotated_from_id BIGINT REFERENCES api_keys(id) ON DELETE SET NULL,
    allowed_ips VARCHAR(1024),
    rate_limit_per_second BIGINT NOT NULL DEFAULT 100,
    rate_limit_per_day BIGINT NOT NULL DEFAULT 8640000,
    created_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 路由规则表
CREATE TABLE IF NOT EXISTS route_rules (
    id BIGSERIAL PRIMARY KEY,
    app_id BIGINT NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    name VARCHAR(128) NOT NULL,
    path_prefix VARCHAR(512) NOT NULL,
    match_type VARCHAR(32) NOT NULL DEFAULT 'PREFIX',
    http_method VARCHAR(16),
    priority INTEGER NOT NULL DEFAULT 0,
    target_backends JSON,
    connect_timeout_ms INTEGER NOT NULL DEFAULT 5000,
    read_timeout_ms INTEGER NOT NULL DEFAULT 30000,
    max_retries INTEGER NOT NULL DEFAULT 0,
    retry_on_5xx BOOLEAN NOT NULL DEFAULT FALSE,
    retry_on_timeout BOOLEAN NOT NULL DEFAULT FALSE,
    retry_interval_ms INTEGER NOT NULL DEFAULT 500,
    request_headers_to_add JSON,
    request_headers_to_remove JSON,
    path_prefix_replacement VARCHAR(512),
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    version INTEGER NOT NULL DEFAULT 1,
    created_by BIGINT REFERENCES admin_users(id) ON DELETE SET NULL,
    approved_by BIGINT REFERENCES admin_users(id) ON DELETE SET NULL,
    published_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 路由规则版本表
CREATE TABLE IF NOT EXISTS route_rule_versions (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT NOT NULL REFERENCES route_rules(id) ON DELETE CASCADE,
    version INTEGER NOT NULL,
    snapshot JSON NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT REFERENCES admin_users(id) ON DELETE SET NULL,
    UNIQUE(rule_id, version)
);

-- 路由规则审批表
CREATE TABLE IF NOT EXISTS route_rule_approvals (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT NOT NULL REFERENCES route_rules(id) ON DELETE CASCADE,
    requester_id BIGINT NOT NULL REFERENCES admin_users(id) ON DELETE CASCADE,
    approver_id BIGINT REFERENCES admin_users(id) ON DELETE SET NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    comment VARCHAR(1024),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_at TIMESTAMP
);

-- 流量染色规则表
CREATE TABLE IF NOT EXISTS traffic_color_rules (
    id BIGSERIAL PRIMARY KEY,
    app_id BIGINT NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    name VARCHAR(128) NOT NULL,
    priority INTEGER NOT NULL DEFAULT 0,
    tag_value VARCHAR(64) NOT NULL,
    condition_type VARCHAR(32) NOT NULL,
    condition_value JSON,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    operation_type VARCHAR(32) NOT NULL DEFAULT 'ADD_TAG',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 限流配置表
CREATE TABLE IF NOT EXISTS rate_limit_configs (
    id BIGSERIAL PRIMARY KEY,
    app_id BIGINT NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    rule_id BIGINT REFERENCES route_rules(id) ON DELETE CASCADE,
    scope VARCHAR(32) NOT NULL,
    limit_per_second BIGINT NOT NULL,
    burst_capacity BIGINT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 熔断器配置表
CREATE TABLE IF NOT EXISTS circuit_breaker_configs (
    id BIGSERIAL PRIMARY KEY,
    app_id BIGINT NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    upstream_service VARCHAR(256) NOT NULL,
    failure_rate_threshold FLOAT NOT NULL DEFAULT 50.0,
    sliding_window_size INTEGER NOT NULL DEFAULT 100,
    minimum_number_of_calls INTEGER NOT NULL DEFAULT 20,
    wait_duration_in_open_state_ms BIGINT NOT NULL DEFAULT 60000,
    permitted_number_of_calls_in_half_open_state INTEGER NOT NULL DEFAULT 10,
    fallback_response_body TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 审计日志表
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    operator_id BIGINT,
    operator_name VARCHAR(128),
    operation_type VARCHAR(32) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id VARCHAR(64),
    old_value JSON,
    new_value JSON,
    remark VARCHAR(1024),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 配额调度表
CREATE TABLE IF NOT EXISTS quota_schedules (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    start_hour INTEGER NOT NULL,
    end_hour INTEGER NOT NULL,
    multiplier FLOAT NOT NULL DEFAULT 1.0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 灰度发布表
CREATE TABLE IF NOT EXISTS gray_releases (
    id BIGSERIAL PRIMARY KEY,
    gray_release_id VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    tenant_id BIGINT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    application_id BIGINT NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    route_rule_id BIGINT NOT NULL REFERENCES route_rules(id) ON DELETE CASCADE,
    color_rule_id BIGINT REFERENCES traffic_color_rules(id) ON DELETE SET NULL,
    status VARCHAR(32) NOT NULL,
    current_phase VARCHAR(32),
    current_traffic_percent INTEGER,
    initial_percent INTEGER NOT NULL,
    release_stages_json JSON,
    observation_minutes_per_stage INTEGER NOT NULL,
    error_rate_threshold DOUBLE PRECISION NOT NULL DEFAULT 5.0,
    current_error_rate DOUBLE PRECISION,
    phase_start_time TIMESTAMP,
    next_stage_time TIMESTAMP,
    total_stages INTEGER,
    completed_stages INTEGER,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- API文档表
CREATE TABLE IF NOT EXISTS api_docs (
    id BIGSERIAL PRIMARY KEY,
    doc_id VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    version VARCHAR(32),
    application_id BIGINT NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- API文档分组表
CREATE TABLE IF NOT EXISTS api_doc_groups (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    sort_order INTEGER NOT NULL DEFAULT 0,
    doc_id BIGINT NOT NULL REFERENCES api_docs(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- API接口定义表
CREATE TABLE IF NOT EXISTS api_endpoints (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    method VARCHAR(16) NOT NULL,
    path VARCHAR(512) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    group_id BIGINT NOT NULL REFERENCES api_doc_groups(id) ON DELETE CASCADE,
    request_params JSON,
    request_schema JSON,
    response_schema JSON,
    status_codes JSON,
    deprecated VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Mock配置表
CREATE TABLE IF NOT EXISTS mock_configs (
    id BIGSERIAL PRIMARY KEY,
    mock_config_id VARCHAR(64) NOT NULL UNIQUE,
    endpoint_id BIGINT NOT NULL REFERENCES api_endpoints(id) ON DELETE CASCADE,
    route_rule_id BIGINT REFERENCES route_rules(id) ON DELETE SET NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    delay_ms INTEGER NOT NULL DEFAULT 0,
    fault_injection_percent INTEGER,
    fault_error_code VARCHAR(16),
    created_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 调试用例表
CREATE TABLE IF NOT EXISTS debug_cases (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    endpoint_id BIGINT NOT NULL REFERENCES api_endpoints(id) ON DELETE CASCADE,
    request_params JSON,
    request_headers JSON,
    request_body JSON,
    expected_response JSON,
    use_mock BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 接口变更记录表
CREATE TABLE IF NOT EXISTS api_change_records (
    id BIGSERIAL PRIMARY KEY,
    endpoint_id BIGINT NOT NULL REFERENCES api_endpoints(id) ON DELETE CASCADE,
    change_type VARCHAR(64) NOT NULL,
    change_summary VARCHAR(128) NOT NULL,
    change_details JSON,
    changed_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 变更记录备注表
CREATE TABLE IF NOT EXISTS change_record_remarks (
    id BIGSERIAL PRIMARY KEY,
    change_record_id BIGINT NOT NULL REFERENCES api_change_records(id) ON DELETE CASCADE,
    field_path VARCHAR(256) NOT NULL,
    remark_type VARCHAR(32) NOT NULL,
    remark VARCHAR(512),
    created_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 测试套件表
CREATE TABLE IF NOT EXISTS test_suites (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    application_id BIGINT NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    case_order JSON,
    dependencies JSON,
    global_variables JSON,
    concurrency_level INTEGER NOT NULL DEFAULT 1,
    created_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 测试套件执行表
CREATE TABLE IF NOT EXISTS test_suite_executions (
    id BIGSERIAL PRIMARY KEY,
    test_suite_id BIGINT NOT NULL REFERENCES test_suites(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL,
    total_cases INTEGER NOT NULL,
    passed_cases INTEGER,
    failed_cases INTEGER,
    total_duration_ms BIGINT,
    case_results JSON,
    executed_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

-- 测试报告表
CREATE TABLE IF NOT EXISTS test_reports (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    test_suite_id BIGINT NOT NULL REFERENCES test_suites(id) ON DELETE CASCADE,
    execution_id BIGINT NOT NULL REFERENCES test_suite_executions(id) ON DELETE CASCADE,
    total_cases INTEGER NOT NULL,
    passed_cases INTEGER NOT NULL,
    failed_cases INTEGER NOT NULL,
    success_rate DOUBLE PRECISION,
    total_duration_ms BIGINT NOT NULL,
    case_details JSON,
    summary JSON,
    remarks VARCHAR(1024),
    created_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- 索引创建
-- ============================================

CREATE INDEX IF NOT EXISTS idx_applications_tenant_id ON applications(tenant_id);
CREATE INDEX IF NOT EXISTS idx_admin_users_tenant_id ON admin_users(tenant_id);
CREATE INDEX IF NOT EXISTS idx_api_keys_tenant_id ON api_keys(tenant_id);
CREATE INDEX IF NOT EXISTS idx_api_keys_status ON api_keys(status);
CREATE INDEX IF NOT EXISTS idx_route_rules_app_id ON route_rules(app_id);
CREATE INDEX IF NOT EXISTS idx_route_rules_status ON route_rules(status);
CREATE INDEX IF NOT EXISTS idx_route_rule_versions_rule_id ON route_rule_versions(rule_id);
CREATE INDEX IF NOT EXISTS idx_route_rule_approvals_rule_id ON route_rule_approvals(rule_id);
CREATE INDEX IF NOT EXISTS idx_route_rule_approvals_status ON route_rule_approvals(status);
CREATE INDEX IF NOT EXISTS idx_traffic_color_rules_app_id ON traffic_color_rules(app_id);
CREATE INDEX IF NOT EXISTS idx_rate_limit_configs_app_id ON rate_limit_configs(app_id);
CREATE INDEX IF NOT EXISTS idx_rate_limit_configs_scope ON rate_limit_configs(scope);
CREATE INDEX IF NOT EXISTS idx_circuit_breaker_configs_app_id ON circuit_breaker_configs(app_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON audit_logs(created_at);
CREATE INDEX IF NOT EXISTS idx_audit_logs_operator_id ON audit_logs(operator_id);
CREATE INDEX IF NOT EXISTS idx_quota_schedules_tenant_id ON quota_schedules(tenant_id);
CREATE INDEX IF NOT EXISTS idx_gray_releases_gray_release_id ON gray_releases(gray_release_id);
CREATE INDEX IF NOT EXISTS idx_gray_releases_application_id ON gray_releases(application_id);
CREATE INDEX IF NOT EXISTS idx_gray_releases_route_rule_id ON gray_releases(route_rule_id);
CREATE INDEX IF NOT EXISTS idx_gray_releases_status ON gray_releases(status);
CREATE INDEX IF NOT EXISTS idx_api_docs_application_id ON api_docs(application_id);
CREATE INDEX IF NOT EXISTS idx_api_docs_status ON api_docs(status);
CREATE INDEX IF NOT EXISTS idx_api_doc_groups_doc_id ON api_doc_groups(doc_id);
CREATE INDEX IF NOT EXISTS idx_api_endpoints_group_id ON api_endpoints(group_id);
CREATE INDEX IF NOT EXISTS idx_mock_configs_endpoint_id ON mock_configs(endpoint_id);
CREATE INDEX IF NOT EXISTS idx_mock_configs_route_rule_id ON mock_configs(route_rule_id);
CREATE INDEX IF NOT EXISTS idx_debug_cases_endpoint_id ON debug_cases(endpoint_id);
CREATE INDEX IF NOT EXISTS idx_api_change_records_endpoint_id ON api_change_records(endpoint_id);
CREATE INDEX IF NOT EXISTS idx_api_change_records_created_at ON api_change_records(created_at);
CREATE INDEX IF NOT EXISTS idx_change_record_remarks_change_record_id ON change_record_remarks(change_record_id);
CREATE INDEX IF NOT EXISTS idx_change_record_remarks_field_path ON change_record_remarks(field_path);
CREATE INDEX IF NOT EXISTS idx_test_suites_application_id ON test_suites(application_id);
CREATE INDEX IF NOT EXISTS idx_test_suites_created_by ON test_suites(created_by);
CREATE INDEX IF NOT EXISTS idx_test_suite_executions_test_suite_id ON test_suite_executions(test_suite_id);
CREATE INDEX IF NOT EXISTS idx_test_suite_executions_status ON test_suite_executions(status);
CREATE INDEX IF NOT EXISTS idx_test_suite_executions_created_at ON test_suite_executions(created_at);
CREATE INDEX IF NOT EXISTS idx_test_reports_test_suite_id ON test_reports(test_suite_id);
CREATE INDEX IF NOT EXISTS idx_test_reports_execution_id ON test_reports(execution_id);
CREATE INDEX IF NOT EXISTS idx_test_reports_created_at ON test_reports(created_at);

-- ============================================
-- 初始数据
-- ============================================

-- 租户数据
INSERT INTO tenants (name, code, description, max_qps, enabled, created_at, updated_at)
VALUES 
    ('支付团队', 'payment', '支付业务团队，负责所有支付相关API', 1000, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('订单团队', 'order', '订单业务团队，负责所有订单相关API', 500, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

-- 默认管理员数据
-- 密码: admin123 (BCrypt加密: $2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi)
INSERT INTO admin_users (username, password, email, role, tenant_id, enabled, created_at, updated_at)
VALUES 
    ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'admin@apigateway.com', 'ADMIN', NULL, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (username) DO NOTHING;
