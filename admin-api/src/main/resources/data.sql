INSERT INTO admin_users (username, password, email, role, tenant_id, enabled, created_at)
SELECT 'admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', 'admin@apigateway.com', 'ADMIN', NULL, TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM admin_users WHERE username = 'admin');
