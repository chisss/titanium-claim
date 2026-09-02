--liquibase formatted sql
--changeset weisun:claim-3
-- 理赔配置子域六张配置表（M2：流程模板/赔付规则/单证模板/时限规则/医院网络/黑名单）
-- 配置子域为纯 JPA CRUD 状态存储（不经过 Axon、不发领域事件），租户维度贯穿，逻辑删除 is_deleted

-- 1. 理赔流程模板：按(险种线,理赔类型)定义环节序列/环节时限/责任角色/必经点
CREATE TABLE t_claim_flow_template
(
    id                   VARCHAR(32)  NOT NULL COMMENT '主键(雪花)',
    tenant_id            VARCHAR(32)  NOT NULL COMMENT '租户ID',
    template_id          VARCHAR(32)  NOT NULL COMMENT '模板ID(雪花)',
    insurance_line       VARCHAR(32)  NOT NULL COMMENT '险种线 code(metadata ClaimEnum.InsuranceLine)',
    claim_type           VARCHAR(50)  NOT NULL COMMENT '理赔类型 code',
    stage_sequence       TEXT         NOT NULL COMMENT '环节序列(JSON数组)',
    stage_time_limits    TEXT         NULL     COMMENT '环节时限小时数(JSON对象:环节->小时)',
    responsible_role     VARCHAR(64)  NULL     COMMENT '责任角色',
    mandatory_checkpoints TEXT        NULL     COMMENT '必经环节检查点(JSON数组)',
    create_time          DATETIME     NOT NULL COMMENT '创建时间',
    update_time          DATETIME     NOT NULL COMMENT '更新时间',
    created_by           VARCHAR(32)  NOT NULL COMMENT '创建人',
    updated_by           VARCHAR(32)  NOT NULL COMMENT '更新人',
    is_deleted           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除(0:否,1:是)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_claim_flow_template_biz (tenant_id, insurance_line, claim_type),
    KEY idx_claim_flow_template_tenant (tenant_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='理赔流程模板配置表';

-- 2. 理赔赔付规则：免赔额/赔付比例/单次与年度限额/医院分档比例/责任免除
CREATE TABLE t_claim_payout_rule
(
    id                  VARCHAR(32)    NOT NULL COMMENT '主键(雪花)',
    tenant_id           VARCHAR(32)    NOT NULL COMMENT '租户ID',
    rule_id             VARCHAR(32)    NOT NULL COMMENT '规则ID(雪花)',
    insurance_line      VARCHAR(32)    NOT NULL COMMENT '险种线 code',
    claim_type          VARCHAR(50)    NOT NULL COMMENT '理赔类型 code',
    deductible          DECIMAL(18, 2) NULL COMMENT '免赔额(元)',
    payout_ratio        INT            NULL COMMENT '赔付比例(0-100百分比)',
    per_claim_limit     DECIMAL(18, 2) NULL COMMENT '单次限额(元,空=不限)',
    annual_limit        DECIMAL(18, 2) NULL COMMENT '年度限额(元,空=不限)',
    hospital_tier_ratios TEXT          NULL COMMENT '医院分档赔付比例(JSON对象:档位->0-100)',
    exclusions          TEXT           NULL COMMENT '责任免除清单(JSON数组)',
    create_time         DATETIME       NOT NULL COMMENT '创建时间',
    update_time         DATETIME       NOT NULL COMMENT '更新时间',
    created_by          VARCHAR(32)    NOT NULL COMMENT '创建人',
    updated_by          VARCHAR(32)    NOT NULL COMMENT '更新人',
    is_deleted          TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除(0:否,1:是)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_claim_payout_rule_biz (tenant_id, insurance_line, claim_type),
    KEY idx_claim_payout_rule_tenant (tenant_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='理赔赔付规则配置表';

-- 3. 理赔单证模板：必填/选填材料清单
CREATE TABLE t_claim_document_template
(
    id                 VARCHAR(32) NOT NULL COMMENT '主键(雪花)',
    tenant_id          VARCHAR(32) NOT NULL COMMENT '租户ID',
    template_id        VARCHAR(32) NOT NULL COMMENT '模板ID(雪花)',
    insurance_line     VARCHAR(32) NOT NULL COMMENT '险种线 code',
    claim_type         VARCHAR(50) NOT NULL COMMENT '理赔类型 code',
    required_documents TEXT        NULL COMMENT '必填材料清单(JSON数组)',
    optional_documents TEXT        NULL COMMENT '选填材料清单(JSON数组)',
    create_time        DATETIME    NOT NULL COMMENT '创建时间',
    update_time        DATETIME    NOT NULL COMMENT '更新时间',
    created_by         VARCHAR(32) NOT NULL COMMENT '创建人',
    updated_by         VARCHAR(32) NOT NULL COMMENT '更新人',
    is_deleted         TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除(0:否,1:是)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_claim_document_template_biz (tenant_id, insurance_line, claim_type),
    KEY idx_claim_document_template_tenant (tenant_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='理赔单证模板配置表';

-- 4. 理赔时限规则：环节处理时限与预警时限
CREATE TABLE t_claim_time_limit_rule
(
    id             VARCHAR(32) NOT NULL COMMENT '主键(雪花)',
    tenant_id      VARCHAR(32) NOT NULL COMMENT '租户ID',
    rule_id        VARCHAR(32) NOT NULL COMMENT '规则ID(雪花)',
    insurance_line VARCHAR(32) NOT NULL COMMENT '险种线 code',
    claim_stage    VARCHAR(64) NOT NULL COMMENT '案件环节 code',
    limit_hours    INT         NOT NULL COMMENT '环节处理时限(小时)',
    alert_hours    INT         NULL COMMENT '预警时限(小时,0=不预警)',
    create_time    DATETIME    NOT NULL COMMENT '创建时间',
    update_time    DATETIME    NOT NULL COMMENT '更新时间',
    created_by     VARCHAR(32) NOT NULL COMMENT '创建人',
    updated_by     VARCHAR(32) NOT NULL COMMENT '更新人',
    is_deleted     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除(0:否,1:是)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_claim_time_limit_rule_biz (tenant_id, insurance_line, claim_stage),
    KEY idx_claim_time_limit_rule_tenant (tenant_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='理赔时限规则配置表';

-- 5. 医院网络：宠物险直赔医院协议状态/赔付比例
CREATE TABLE t_claim_hospital_network
(
    id                VARCHAR(32) NOT NULL COMMENT '主键(雪花)',
    tenant_id         VARCHAR(32) NOT NULL COMMENT '租户ID',
    hospital_id       VARCHAR(32) NOT NULL COMMENT '医院ID(雪花)',
    hospital_name     VARCHAR(128) NOT NULL COMMENT '医院名称',
    hospital_level    VARCHAR(32) NULL COMMENT '医院等级',
    agreement_status  VARCHAR(32) NOT NULL COMMENT '协议状态 code(ACTIVE/SUSPENDED/TERMINATED)',
    payout_ratio      INT         NULL COMMENT '定点赔付比例(0-100百分比)',
    direct_settlement TINYINT(1)  NULL COMMENT '是否直赔医院(0:否,1:是)',
    address           VARCHAR(256) NULL COMMENT '医院地址',
    contact_phone     VARCHAR(32) NULL COMMENT '联系电话',
    create_time       DATETIME    NOT NULL COMMENT '创建时间',
    update_time       DATETIME    NOT NULL COMMENT '更新时间',
    created_by        VARCHAR(32) NOT NULL COMMENT '创建人',
    updated_by        VARCHAR(32) NOT NULL COMMENT '更新人',
    is_deleted        TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除(0:否,1:是)',
    PRIMARY KEY (id),
    KEY idx_claim_hospital_network_tenant (tenant_id),
    KEY idx_claim_hospital_network_name (tenant_id, hospital_name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='理赔医院网络配置表';

-- 6. 理赔黑名单：人员/车辆/医院/修理厂反欺诈名单
CREATE TABLE t_claim_blacklist
(
    id             VARCHAR(32)  NOT NULL COMMENT '主键(雪花)',
    tenant_id      VARCHAR(32)  NOT NULL COMMENT '租户ID',
    blacklist_id   VARCHAR(32)  NOT NULL COMMENT '黑名单ID(雪花)',
    subject_type   VARCHAR(32)  NOT NULL COMMENT '标的类型 code(PERSON/VEHICLE/HOSPITAL/REPAIR_SHOP)',
    subject_id     VARCHAR(64)  NOT NULL COMMENT '标的主键(人员ID/车牌/医院ID/修理厂ID)',
    subject_name   VARCHAR(128) NULL COMMENT '标的名称',
    reason_code    VARCHAR(64)  NOT NULL COMMENT '拉黑原因 code',
    status         VARCHAR(32)  NOT NULL COMMENT '生效状态 code(ACTIVE/REVOKED)',
    effective_time DATETIME     NULL COMMENT '生效时间',
    create_time    DATETIME     NOT NULL COMMENT '创建时间',
    update_time    DATETIME     NOT NULL COMMENT '更新时间',
    created_by     VARCHAR(32)  NOT NULL COMMENT '创建人',
    updated_by     VARCHAR(32)  NOT NULL COMMENT '更新人',
    is_deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除(0:否,1:是)',
    PRIMARY KEY (id),
    KEY idx_claim_blacklist_tenant (tenant_id),
    KEY idx_claim_blacklist_subject (tenant_id, subject_type, subject_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='理赔黑名单配置表';
--rollback DROP TABLE t_claim_blacklist, t_claim_hospital_network, t_claim_time_limit_rule, t_claim_document_template, t_claim_payout_rule, t_claim_flow_template;
