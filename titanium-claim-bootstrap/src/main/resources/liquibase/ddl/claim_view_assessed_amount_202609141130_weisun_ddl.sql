--liquibase formatted sql
--changeset weisun:claim-8
-- 理赔案件读模型表扩展（对齐 ClaimView.java 新增定损核定额列）
-- 定损在案的案件，核赔结算金额取定损核定额（=（定损总金额−残值）×责任比例），不再由人工透传。
-- 该列供结案环节带出「按定损应赔金额」；未定损案件为 NULL（不以 0 冒充，调用方据此区分有无定损依据）。
ALTER TABLE t_claim_view
    ADD COLUMN assessed_payable_amount DECIMAL(18, 2) COMMENT '按定损核定的应赔金额((定损总金额-残值)×责任比例;未定损为NULL)';
--rollback ALTER TABLE t_claim_view DROP COLUMN assessed_payable_amount;
