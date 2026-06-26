-- 初始化理赔案件表结构

-- 删除已存在的表（如果存在）
DROP TABLE IF EXISTS t_claim;

-- 创建理赔案件表
CREATE TABLE t_claim (
    claim_id VARCHAR(36) NOT NULL PRIMARY KEY,
    customer_id VARCHAR(36) NOT NULL,
    policy_id VARCHAR(36) NOT NULL,
    claim_number VARCHAR(50) NOT NULL UNIQUE,
    claim_type VARCHAR(50) NOT NULL,
    incident_date DATETIME NOT NULL,
    incident_description TEXT NOT NULL,
    claim_amount DECIMAL(15, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    
    -- 添加索引
    INDEX idx_customer_id (customer_id),
    INDEX idx_policy_id (policy_id),
    INDEX idx_claim_number (claim_number),
    INDEX idx_status (status),
    INDEX idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 添加注释
COMMENT ON TABLE t_claim IS '理赔案件表';
COMMENT ON COLUMN t_claim.claim_id IS '理赔案件ID';
COMMENT ON COLUMN t_claim.customer_id IS '客户ID';
COMMENT ON COLUMN t_claim.policy_id IS '保单ID';
COMMENT ON COLUMN t_claim.claim_number IS '理赔编号';
COMMENT ON COLUMN t_claim.claim_type IS '理赔类型';
COMMENT ON COLUMN t_claim.incident_date IS '事故日期';
COMMENT ON COLUMN t_claim.incident_description IS '事故描述';
COMMENT ON COLUMN t_claim.claim_amount IS '理赔金额';
COMMENT ON COLUMN t_claim.status IS '理赔状态';
COMMENT ON COLUMN t_claim.created_at IS '创建时间';
COMMENT ON COLUMN t_claim.updated_at IS '更新时间';
COMMENT ON COLUMN t_claim.tenant_id IS '租户ID';
