--liquibase formatted sql
-- 说明：以下业务表无对应聚合根/读模型实体，依据《全域DDL重建方案清单》§3.8 建立，字段为方案清单标注字段。
--changeset weisun:claim-2
-- 理赔材料表(死亡证明/受益人身份等)，依方案清单 §3.8
CREATE TABLE IF NOT EXISTS t_claim_evidence (
    id            VARCHAR(32)  NOT NULL COMMENT '主键(雪花)',
    claim_id      VARCHAR(36)  NOT NULL COMMENT '理赔案件ID',
    evidence_type VARCHAR(50)  NOT NULL COMMENT '材料类型',
    doc_url       VARCHAR(512)          COMMENT '文档地址',
    verified      TINYINT      NOT NULL DEFAULT 0 COMMENT '已核验(0否1是)',
    tenant_id     VARCHAR(32)  NOT NULL COMMENT '租户ID',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by    VARCHAR(32)  NOT NULL DEFAULT 'system' COMMENT '创建人',
    updated_by    VARCHAR(32)  NOT NULL DEFAULT 'system' COMMENT '更新人',
    is_deleted    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除(0否1是)',
    PRIMARY KEY (id),
    KEY idx_claim_evidence_tenant (tenant_id),
    KEY idx_claim_evidence_claim (claim_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='理赔材料表(方案清单§3.8,无聚合根实体)';
--rollback DROP TABLE IF EXISTS t_claim_evidence;

--changeset weisun:claim-3
-- 给付记录表，依方案清单 §3.8
CREATE TABLE IF NOT EXISTS t_claim_settlement (
    id             VARCHAR(32)   NOT NULL COMMENT '主键(雪花)',
    claim_id       VARCHAR(36)   NOT NULL COMMENT '理赔案件ID',
    benefit_amount DECIMAL(18,2) NOT NULL COMMENT '给付金额',
    payee_id       VARCHAR(36)            COMMENT '收款人ID',
    settle_time    DATETIME               COMMENT '给付时间',
    tenant_id      VARCHAR(32)   NOT NULL COMMENT '租户ID',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by     VARCHAR(32)   NOT NULL DEFAULT 'system' COMMENT '创建人',
    updated_by     VARCHAR(32)   NOT NULL DEFAULT 'system' COMMENT '更新人',
    is_deleted     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除(0否1是)',
    PRIMARY KEY (id),
    KEY idx_claim_settlement_tenant (tenant_id),
    KEY idx_claim_settlement_claim (claim_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='给付记录表(方案清单§3.8,无聚合根实体)';
--rollback DROP TABLE IF EXISTS t_claim_settlement;

--changeset weisun:claim-4
-- 年金生存给付流水表，依方案清单 §3.8
CREATE TABLE IF NOT EXISTS t_annuity_benefit (
    id          VARCHAR(32)   NOT NULL COMMENT '主键(雪花)',
    policy_id   VARCHAR(36)   NOT NULL COMMENT '保单ID',
    period      VARCHAR(32)            COMMENT '给付期次',
    amount      DECIMAL(18,2) NOT NULL COMMENT '给付金额',
    pay_date    DATETIME               COMMENT '给付日期',
    tenant_id   VARCHAR(32)   NOT NULL COMMENT '租户ID',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by  VARCHAR(32)   NOT NULL DEFAULT 'system' COMMENT '创建人',
    updated_by  VARCHAR(32)   NOT NULL DEFAULT 'system' COMMENT '更新人',
    is_deleted  TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除(0否1是)',
    PRIMARY KEY (id),
    KEY idx_annuity_benefit_tenant (tenant_id),
    KEY idx_annuity_benefit_policy (policy_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='年金生存给付流水表(方案清单§3.8,无聚合根实体)';
--rollback DROP TABLE IF EXISTS t_annuity_benefit;
