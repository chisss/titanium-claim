--liquibase formatted sql

-- 赔付规则**平台默认种子**（G12/g12-05b，G12/AC-08 的种子半边 —— 把 claim-bootstrap 的
-- liquibase 从「零 dml」变为含种子）。
--
-- 【为何是这三行 —— 下界独立给出，不取「当前有几行就填几行」】
--   下界 = 本批次（G12 人身险特化）覆盖的三条险种线各自取一个代表三级险种 = 3：
--     ① 寿险线（InsuranceLine.LIFE）    → TERM_LIFE（定期寿险：纯保障型代表；
--        WHOLE_LIFE/ENDOWMENT/ANNUITY 含现金价值/生存给付，与费用报销型的本表结构不适配，
--        待业务确认给付结构后按需追加）
--     ② 重疾线（InsuranceLine.HEALTH）  → CRITICAL_ILLNESS（重大疾病保险）
--     ③ 医疗线（InsuranceLine.HEALTH）  → MEDICAL（医疗保险）
--   依据：G12 文档 §g12-05「按 G10 权威险种编码，为本批次覆盖的险种线（寿险/重疾/医疗）落
--   赔付规则种子数据」；insurance_line 取 G10 权威三级码（metadata InsuranceProductType.code）。
--
-- 【键值域 —— 为何是 platform 租户 + ClaimEnum.ClaimType 理赔类型】
--   tenant_id = 'platform'：赔付规则读取优先级「租户覆盖 → 平台默认」
--     （ReimbursementAdjustmentQueryService 经 findPlatformDefault 兜底）——本种子是**平台默认
--     模板**，不得写成租户专属（G12 文档 §g12-05 风险段明示）。
--   claim_type 取 metadata ClaimEnum.ClaimType 的权威 code —— 该值域是真实链路强制口径，两处实读：
--     ① 案件创建 `ClaimEnum.ClaimType.fromCode(request.getClaimType())`
--        （ClaimRegistrationOrchestrator:61，非法码在此被拒）；
--     ② admin 理赔类型字典种子已「对齐 metadata ClaimEnum.ClaimType」（admin_claim_dict 脚本内注）。
--     重疾「确诊定额给付」在现值域（MEDICAL/ACCIDENT/DEATH/DISABILITY/PROPERTY/LIABILITY）中
--     就近归 MEDICAL；若业务需要独立「重疾理赔」类型，须先扩 ClaimEnum.ClaimType 值域、再改本表键。
--
-- 【档位值 —— 🔴 示例基线（AI 推荐 2026-10-13），业务确认后替换】
--   G12 文档 §六 R-1：「赔付规则种子的覆盖范围与档位值在系统内无任何依据，须业务确认」。
--   本文件按用户裁示（2026-10-13）先落行业典型示例基线并在本注释标注；替换时直接改本文件
--   （changeset 已 runOnChange:true，内容变更自动重跑，见下「幂等」）。
--   逐行取值参照（费用补偿型医疗险的通行形态 + 定额给付险的典型保额）：
--     · MEDICAL × MEDICAL          ：免赔 100 元 / 比例 80% / 单次限额 1 万 / 年度限额 30 万
--     · CRITICAL_ILLNESS × MEDICAL ：免赔 0 / 比例 100% / 单次=年度限额 30 万（重疾典型保额）
--     · TERM_LIFE × DEATH          ：免赔 0 / 比例 100% / 单次=年度限额 100 万（定寿典型保额）
--   hospital_tier_ratios / social_security_ratios 留 NULL：医院等级档（g12-04）与社保身份档
--     （g12-03）的**档位值域**业务未确认（R-2 口径），空 = 未启用该维度、理算走既有定点/非定点
--     路径（向后兼容，列注释已载明）；业务确认后以 UPDATE 追加 JSON 文本。
--   exclusions 留 NULL：责任免除清单须业务确认后录入，不由本种子预设。
--
-- 【幂等】runOnChange:true + 先删后插（admin 字典种子同模式，admin_claim_dict 脚本内注）：
--   重复执行零副作用；本文件内容变更时自动重跑。DELETE 精确限定本变更集拥有的三个业务键
--   （tenant_id='platform' × 三行），不动任何租户覆盖行。
-- 【时间列】create_time/update_time 用字面量而非 NOW()：种子须可重复执行得到同一结果，
--   且 NOW() 的括号会污染校验脚本对该文件的元组计数（feature-center 种子同纪律）。
--changeset weisun:claim-payout-rule-seed-202610130957 runOnChange:true

DELETE FROM t_claim_payout_rule
 WHERE tenant_id = 'platform'
   AND (   (insurance_line = 'TERM_LIFE'        AND claim_type = 'DEATH')
        OR (insurance_line = 'CRITICAL_ILLNESS' AND claim_type = 'MEDICAL')
        OR (insurance_line = 'MEDICAL'          AND claim_type = 'MEDICAL'));

INSERT INTO t_claim_payout_rule
    (id, tenant_id, rule_id, insurance_line, claim_type, deductible, payout_ratio,
     per_claim_limit, annual_limit, hospital_tier_ratios, social_security_ratios, exclusions,
     create_time, update_time, created_by, updated_by, is_deleted)
VALUES
    ('seed-payout-term-life', 'platform', 'seed-payout-term-life', 'TERM_LIFE', 'DEATH',
     0.00, 100, 1000000.00, 1000000.00, NULL, NULL, NULL,
     '2026-10-13 00:00:00', '2026-10-13 00:00:00', 'seed', 'seed', 0),
    ('seed-payout-critical-illness', 'platform', 'seed-payout-critical-illness', 'CRITICAL_ILLNESS', 'MEDICAL',
     0.00, 100, 300000.00, 300000.00, NULL, NULL, NULL,
     '2026-10-13 00:00:00', '2026-10-13 00:00:00', 'seed', 'seed', 0),
    ('seed-payout-medical', 'platform', 'seed-payout-medical', 'MEDICAL', 'MEDICAL',
     100.00, 80, 10000.00, 300000.00, NULL, NULL, NULL,
     '2026-10-13 00:00:00', '2026-10-13 00:00:00', 'seed', 'seed', 0);

--rollback DELETE FROM t_claim_payout_rule WHERE tenant_id = 'platform' AND id IN ('seed-payout-term-life', 'seed-payout-critical-illness', 'seed-payout-medical');
