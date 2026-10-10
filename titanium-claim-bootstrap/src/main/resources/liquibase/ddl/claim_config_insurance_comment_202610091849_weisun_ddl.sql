--liquibase formatted sql
--changeset weisun:claim-config-insurance-comment-20261009
-- G10/g10-02：修正 4 处 insurance_line 列注释。原注释引用不存在的枚举 ClaimEnum.InsuranceLine
-- （metadata ClaimEnum 仅含 ReportStatus/SurveyStatus/AuditStatus/PaymentStatus/ClaimType）。
-- 实读偏差登记：文档称「四处均引用 ClaimEnum.InsuranceLine」，实为仅 t_claim_flow_template 一处引用，
-- 其余三处为泛化「险种线 code」——四处于本 changeset 同批收敛为同一准确文案。
ALTER TABLE t_claim_flow_template     MODIFY COLUMN insurance_line VARCHAR(32) NOT NULL COMMENT '险种编码(权威三级码, metadata InsuranceProductType; 存量裸串待 g10-03 对账收敛)';
ALTER TABLE t_claim_payout_rule       MODIFY COLUMN insurance_line VARCHAR(32) NOT NULL COMMENT '险种编码(权威三级码, metadata InsuranceProductType; 存量裸串待 g10-03 对账收敛)';
ALTER TABLE t_claim_document_template MODIFY COLUMN insurance_line VARCHAR(32) NOT NULL COMMENT '险种编码(权威三级码, metadata InsuranceProductType; 存量裸串待 g10-03 对账收敛)';
ALTER TABLE t_claim_time_limit_rule   MODIFY COLUMN insurance_line VARCHAR(32) NOT NULL COMMENT '险种编码(权威三级码, metadata InsuranceProductType; 存量裸串待 g10-03 对账收敛)';
--rollback ALTER TABLE t_claim_flow_template MODIFY COLUMN insurance_line VARCHAR(32) NOT NULL COMMENT '险种线 code(metadata ClaimEnum.InsuranceLine)'; ALTER TABLE t_claim_payout_rule MODIFY COLUMN insurance_line VARCHAR(32) NOT NULL COMMENT '险种线 code'; ALTER TABLE t_claim_document_template MODIFY COLUMN insurance_line VARCHAR(32) NOT NULL COMMENT '险种线 code'; ALTER TABLE t_claim_time_limit_rule MODIFY COLUMN insurance_line VARCHAR(32) NOT NULL COMMENT '险种线 code';
