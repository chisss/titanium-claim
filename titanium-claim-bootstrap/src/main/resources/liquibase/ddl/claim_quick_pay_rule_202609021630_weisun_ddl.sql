--liquibase formatted sql
--changeset weisun:claim-6
-- 快赔规则配置表（产品文档 §2.10 小额快赔通道：按理赔类型配置金额阈值与开关，租户覆盖>平台默认）
CREATE TABLE t_claim_quick_pay_rule
(
    id               VARCHAR(32)    NOT NULL COMMENT '主键(雪花)',
    tenant_id        VARCHAR(32)    NOT NULL COMMENT '租户ID(平台默认 platform)',
    rule_id          VARCHAR(32)    NOT NULL COMMENT '规则ID(雪花)',
    claim_type       VARCHAR(50)    NOT NULL COMMENT '适用理赔类型 code',
    enabled          TINYINT        NOT NULL DEFAULT 1 COMMENT '通道开关(1:开启,0:关闭)',
    amount_threshold DECIMAL(18, 2) NOT NULL COMMENT '快赔金额阈值(元,案件金额<=阈值方可自动核赔)',
    create_time      DATETIME       NOT NULL COMMENT '创建时间',
    update_time      DATETIME       NOT NULL COMMENT '更新时间',
    created_by       VARCHAR(32)    NOT NULL COMMENT '创建人',
    updated_by       VARCHAR(32)    NOT NULL COMMENT '更新人',
    is_deleted       TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除(0:否,1:是)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_claim_quick_pay_rule_biz (tenant_id, claim_type),
    KEY idx_claim_quick_pay_rule_tenant (tenant_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='快赔规则配置表';
--rollback DROP TABLE t_claim_quick_pay_rule;
