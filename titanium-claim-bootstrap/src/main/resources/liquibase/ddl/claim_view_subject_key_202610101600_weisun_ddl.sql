--liquibase formatted sql

--changeset weisun:claim-view-subject-key-202610101600
-- G11/g11-03：理赔读模型新增标的键两列（对齐 ClaimView.java 新增 subject_id / vin）。
-- 背景：现行 Claim 聚合在读模型侧**无任何车辆/标的标识**，「按车辆（VIN/标的标识）检索历史赔案」
-- （product 域反算 NCD 的取数出口）无键可查——本 changeset 补此缺口。
-- 语义：subject_id 保单内唯一（车险为被保车辆标的）；vin 为车辆识别代号，是**跨保单**的车辆身份
-- （续保换单后 policyId/subjectId 均变，唯 VIN 不变），故 NCD 历史赔案检索以 vin 为主键。
-- 两列均可空：历史行为 null（存量赔案不可按车辆检索，属预期，非数据错误）。
ALTER TABLE t_claim_view
    ADD COLUMN subject_id VARCHAR(64) COMMENT '标的ID(保单内唯一;车险为被保车辆标的;G11/g11-03)',
    ADD COLUMN vin VARCHAR(64) COMMENT '车辆识别代号(车险车辆身份,跨保单历史赔案检索键;G11/g11-03)',
    ADD KEY idx_claim_view_subject (subject_id, tenant_id),
    ADD KEY idx_claim_view_vin (vin, tenant_id);
--rollback ALTER TABLE t_claim_view DROP COLUMN subject_id, DROP COLUMN vin, DROP KEY idx_claim_view_subject, DROP KEY idx_claim_view_vin;
