--liquibase formatted sql
--changeset weisun:claim-2
-- 理赔案件读模型表扩展（对齐 ClaimView.java 新增支付闭环字段：赔付状态/支付单号/拒赔原因/拒赔时间/结案时间）
ALTER TABLE t_claim_view
    ADD COLUMN payment_status   VARCHAR(20) COMMENT '赔付状态(PROCESSING/SUCCESS/FAILED/CLOSED/REJECTED_CLOSED)',
    ADD COLUMN payment_no       VARCHAR(50) COMMENT '支付单号(支付域出账成功回写)',
    ADD COLUMN rejection_reason VARCHAR(50) COMMENT '拒赔原因编码(RejectReason code)',
    ADD COLUMN rejected_at      DATETIME    COMMENT '拒赔时间',
    ADD COLUMN closed_at        DATETIME    COMMENT '结案时间';
--rollback ALTER TABLE t_claim_view DROP COLUMN payment_status, DROP COLUMN payment_no, DROP COLUMN rejection_reason, DROP COLUMN rejected_at, DROP COLUMN closed_at;
