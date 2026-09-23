--liquibase formatted sql

--changeset weisun:claim-do-version-202609231700
--comment m20-09 / PER-01：写侧 DO 补 @Version 乐观锁列。原实现无 @Version，JPA 走无条件 UPDATE，并发更新后写者静默覆盖前写者；补列后 UPDATE 带 WHERE version=?，冲突抛 ObjectOptimisticLockingFailureException（映射 409）。注：本域原本已有 @Version 的 DO（如 t_billing_payment_allocation）不在本变更内。
ALTER TABLE t_claim_blacklist ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本(JPA维护)';
ALTER TABLE t_claim_document_template ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本(JPA维护)';
ALTER TABLE t_claim_flow_template ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本(JPA维护)';
ALTER TABLE t_claim_hospital_network ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本(JPA维护)';
ALTER TABLE t_claim_payout_rule ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本(JPA维护)';
ALTER TABLE t_claim_quick_pay_rule ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本(JPA维护)';
ALTER TABLE t_claim_time_limit_rule ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本(JPA维护)';
