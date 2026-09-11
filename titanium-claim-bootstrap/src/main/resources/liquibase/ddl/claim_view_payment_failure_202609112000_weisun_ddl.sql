--liquibase formatted sql
--changeset weisun:claim-7
-- 理赔案件读模型表扩展（对齐 ClaimView.java 新增赔付失败列）
-- 支付域出账未成功（渠道确认失败 / 人工取消）回写时，赔付状态置 FAILED 但**案件状态保持 APPROVED**
-- 待人工重派——故这三列与 status 无关，只描述「出款这一跳」的结果。
ALTER TABLE t_claim_view
    ADD COLUMN payment_failure_type VARCHAR(20) COMMENT '赔付失败类型(PaymentFailureType code: FAILED渠道确认失败/CANCELLED人工取消)',
    ADD COLUMN payment_failure_reason VARCHAR(500) COMMENT '赔付失败原因(渠道返回的失败原因或人工取消原因)',
    ADD COLUMN payment_failed_at DATETIME COMMENT '赔付失败时间';
--rollback ALTER TABLE t_claim_view DROP COLUMN payment_failure_type, DROP COLUMN payment_failure_reason, DROP COLUMN payment_failed_at;
