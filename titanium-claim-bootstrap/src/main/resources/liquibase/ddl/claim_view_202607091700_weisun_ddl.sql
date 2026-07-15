--liquibase formatted sql
--changeset weisun:claim-1
-- 理赔案件读模型表（对齐 ClaimView.java，主键 claim_id）
CREATE TABLE IF NOT EXISTS t_claim_view (
    claim_id             VARCHAR(36)   NOT NULL COMMENT '理赔案件ID(聚合根ID,读模型主键)',
    customer_id          VARCHAR(36)            COMMENT '客户ID',
    policy_id            VARCHAR(36)            COMMENT '保单ID',
    claim_number         VARCHAR(50)            COMMENT '理赔编号',
    claim_type           VARCHAR(50)            COMMENT '理赔类型',
    incident_date        DATETIME               COMMENT '出险日期',
    incident_description TEXT                   COMMENT '出险描述',
    claim_amount         DECIMAL(18,2)          COMMENT '理赔金额',
    status               VARCHAR(20)            COMMENT '理赔状态',
    phase                VARCHAR(20)            COMMENT '理赔处理阶段',
    settled_amount       DECIMAL(18,2)          COMMENT '核定赔付金额',
    tenant_id            VARCHAR(32)   NOT NULL COMMENT '租户ID',
    version              BIGINT                 COMMENT '乐观锁版本',
    create_time          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '投影创建时间',
    update_time          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '投影更新时间',
    PRIMARY KEY (claim_id),
    KEY idx_claim_view_tenant (tenant_id),
    KEY idx_claim_view_policy (policy_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='理赔案件读模型表';
--rollback DROP TABLE IF EXISTS t_claim_view;
