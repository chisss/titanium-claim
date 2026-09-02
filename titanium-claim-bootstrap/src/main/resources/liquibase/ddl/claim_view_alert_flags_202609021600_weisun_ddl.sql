--liquibase formatted sql
--changeset weisun:claim-5
-- 理赔案件读模型表扩展（对齐 ClaimView.java 新增反欺诈警示标记列：AlertType code 逗号分隔，快赔通道判据的数据来源）
ALTER TABLE t_claim_view
    ADD COLUMN alert_flags VARCHAR(200) COMMENT '反欺诈警示与统计口径标记(AlertType code 逗号分隔,如 LATE_REPORT,MULTIPLE_REPORTS)';
--rollback ALTER TABLE t_claim_view DROP COLUMN alert_flags;
