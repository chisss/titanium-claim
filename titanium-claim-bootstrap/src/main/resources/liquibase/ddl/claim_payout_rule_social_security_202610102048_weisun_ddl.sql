--liquibase formatted sql

--changeset weisun:claim-payout-rule-social-security-202610102048
-- G12/g12-03：赔付规则表新增社保身份分档比例列（对齐 ClaimPayoutRule 新增 socialSecurityRatios）。
-- 背景：人身险（医疗/重疾/寿险）赔付比例按社保身份分档（有社保高比例、无社保低比例），现行
-- hospital_tier_ratios 是「医院档位→比例」的单维度表达，无法承载社保维度——本 changeset 补齐。
-- 语义：JSON 对象文本（身份码→0-100 百分比），与 hospital_tier_ratios 同形态；NULL/空 = 未启用社保维度，
-- 理算走既有定点/非定点路径（向后兼容，老配置不受影响）。
-- 🔴 社保身份值域与取数优先级待 R-2 业务确认，本列当前承载开放身份码，确认后由 metadata 枚举收口。
ALTER TABLE t_claim_payout_rule
    ADD COLUMN social_security_ratios TEXT COMMENT '社保身份分档赔付比例(JSON:身份码→0-100;空=未启用该维度;G12/g12-03)'
        AFTER hospital_tier_ratios;
--rollback ALTER TABLE t_claim_payout_rule DROP COLUMN social_security_ratios;
