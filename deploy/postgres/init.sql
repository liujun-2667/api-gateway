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
