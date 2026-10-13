package com.titanium.claim.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.titanium.claim.aggregate.ClaimHospitalNetwork;
import com.titanium.claim.aggregate.ClaimPayoutRule;
import com.titanium.claim.common.enums.config.HospitalAgreementStatus;
import com.titanium.claim.common.enums.config.SettlementChannel;
import com.titanium.claim.service.impl.ReimbursementAdjustmentServiceImpl;
import com.titanium.claim.valueobject.ReimbursementAdjustmentRequest;
import com.titanium.claim.valueobject.ReimbursementAdjustmentRequest.ReimbursementAdjustmentResult;
import com.titanium.claim.valueobject.ReimbursementCalculation;
import com.titanium.metadata.errorcode.ClaimErrorCode;
import com.titanium.metadata.exception.DomainException;

/**
 * 报销理算领域服务测试（定点/非定点裁决 + 金额理算）
 * <p>
 * 验证产品文档 §2.7 理算公式与医院网络校验规则的落地：
 * 定点套台账比例（缺省回落规则基础比例）、非定点套非定点档位（缺省回落基础比例半数）、
 * 单次限额封顶、配置缺失拒绝理算。纯领域服务可脱离容器直测。
 * </p>
 */
@DisplayName("报销理算领域服务")
class ReimbursementAdjustmentServiceTest {

    private static final String TENANT_ID = "tenant-001";

    private final ReimbursementAdjustmentService service = new ReimbursementAdjustmentServiceImpl();

    private ClaimPayoutRule payoutRule(Integer payoutRatio, BigDecimal deductible, BigDecimal perClaimLimit,
                                       Map<String, Integer> hospitalTierRatios) {
        return ClaimPayoutRule.create("rule-1", TENANT_ID, "PET", "PET_MEDICAL",
                deductible, payoutRatio, perClaimLimit, null, hospitalTierRatios, null, List.of());
    }

    private ClaimHospitalNetwork designatedHospital(Integer payoutRatio) {
        return ClaimHospitalNetwork.create("hosp-1", TENANT_ID, "爱宠宠物医院", "二级",
                HospitalAgreementStatus.ACTIVE, payoutRatio, true, "朝阳区", "13800000000");
    }

    private ClaimHospitalNetwork designatedHospitalOfLevel(String level, Integer payoutRatio) {
        return ClaimHospitalNetwork.create("hosp-2", TENANT_ID, "康宁医院", level,
                HospitalAgreementStatus.ACTIVE, payoutRatio, true, "海淀区", "13800000001");
    }

    @Nested
    @DisplayName("定点医院裁决")
    class DesignatedTest {

        @Test
        @DisplayName("台账 ACTIVE → 定点，套台账赔付比例")
        void shouldUseHospitalRatioWhenDesignated() {
            ClaimPayoutRule rule = payoutRule(60, new BigDecimal("100.00"), null, null);
            ClaimHospitalNetwork hospital = designatedHospital(80);

            ReimbursementAdjustmentResult result = service.adjust(new ReimbursementAdjustmentRequest(
                    new BigDecimal("1000.00"), rule, hospital));

            assertEquals(SettlementChannel.DESIGNATED, result.settlementChannel());
            assertEquals(Integer.valueOf(80), result.payoutRatioUsed());
            // (1000 - 100) × 80% = 720.00
            assertEquals(0, result.calculation().payableAmount().compareTo(new BigDecimal("720.00")));
        }

        @Test
        @DisplayName("台账未配置比例 → 回落规则基础比例")
        void shouldFallbackToRuleRatioWhenHospitalRatioMissing() {
            ClaimPayoutRule rule = payoutRule(60, new BigDecimal("100.00"), null, null);
            ClaimHospitalNetwork hospital = designatedHospital(null);

            ReimbursementAdjustmentResult result = service.adjust(new ReimbursementAdjustmentRequest(
                    new BigDecimal("1000.00"), rule, hospital));

            assertEquals(SettlementChannel.DESIGNATED, result.settlementChannel());
            assertEquals(Integer.valueOf(60), result.payoutRatioUsed());
        }

        @Test
        @DisplayName("台账非 ACTIVE（暂停）→ 视为非定点")
        void shouldTreatSuspendedAsNonDesignated() {
            ClaimPayoutRule rule = payoutRule(60, new BigDecimal("100.00"), null,
                    Map.of("NON_DESIGNATED", 40));
            ClaimHospitalNetwork suspended = designatedHospital(80).suspend();

            ReimbursementAdjustmentResult result = service.adjust(new ReimbursementAdjustmentRequest(
                    new BigDecimal("1000.00"), rule, suspended));

            assertEquals(SettlementChannel.NON_DESIGNATED, result.settlementChannel());
            assertEquals(Integer.valueOf(40), result.payoutRatioUsed());
        }
    }

    @Nested
    @DisplayName("非定点医院裁决")
    class NonDesignatedTest {

        @Test
        @DisplayName("不在台账 → 非定点，套 NON_DESIGNATED 档位")
        void shouldUseNonDesignatedTier() {
            ClaimPayoutRule rule = payoutRule(60, new BigDecimal("100.00"), null,
                    Map.of("NON_DESIGNATED", 40));

            ReimbursementAdjustmentResult result = service.adjust(new ReimbursementAdjustmentRequest(
                    new BigDecimal("1000.00"), rule, null));

            assertEquals(SettlementChannel.NON_DESIGNATED, result.settlementChannel());
            assertEquals(Integer.valueOf(40), result.payoutRatioUsed());
            // (1000 - 100) × 40% = 360.00
            assertEquals(0, result.calculation().payableAmount().compareTo(new BigDecimal("360.00")));
        }

        @Test
        @DisplayName("未配置非定点档位 → 回落基础比例半数")
        void shouldFallbackToHalfOfBaseRatio() {
            ClaimPayoutRule rule = payoutRule(70, BigDecimal.ZERO, null, null);

            ReimbursementAdjustmentResult result = service.adjust(new ReimbursementAdjustmentRequest(
                    new BigDecimal("1000.00"), rule, null));

            assertEquals(Integer.valueOf(35), result.payoutRatioUsed());
            assertEquals(0, result.calculation().payableAmount().compareTo(new BigDecimal("350.00")));
        }

        @Test
        @DisplayName("规则未配置基础比例 → 拒绝理算")
        void shouldRejectWhenNoRatioConfigured() {
            ClaimPayoutRule rule = payoutRule(null, BigDecimal.ZERO, null, null);

            DomainException ex = assertThrows(DomainException.class,
                    () -> service.adjust(new ReimbursementAdjustmentRequest(
                            new BigDecimal("1000.00"), rule, null)));
            assertTrue(ex.getMessage().contains("赔付比例"));
        }
    }

    @Nested
    @DisplayName("社保身份裁决（G12/g12-03、AC-03）")
    class SocialSecurityTest {

        /** 社保分档：在职 80% / 居民 60% / 无社保 40%（身份码值域待 R-2 确认，此处以占位码验证机制） */
        private ClaimPayoutRule socialSecurityRule() {
            return ClaimPayoutRule.create("rule-1", TENANT_ID, "MEDICAL", "MEDICAL_REIMBURSE",
                    new BigDecimal("100.00"), 60, null, null, null,
                    Map.of("EMPLOYEE", 80, "RESIDENT", 60, "NONE", 40), List.of());
        }

        @Test
        @DisplayName("AC-03 正向：同一合规费用下，社保内外身份产出不同赔付额")
        void shouldPayDifferentAmountsAcrossIdentities() {
            ClaimPayoutRule rule = socialSecurityRule();
            BigDecimal expense = new BigDecimal("1000.00");

            ReimbursementAdjustmentResult insured = service.adjust(new ReimbursementAdjustmentRequest(
                    expense, rule, null, "EMPLOYEE"));
            ReimbursementAdjustmentResult uninsured = service.adjust(new ReimbursementAdjustmentRequest(
                    expense, rule, null, "NONE"));

            assertEquals(Integer.valueOf(80), insured.payoutRatioUsed());
            assertEquals(0, insured.calculation().payableAmount().compareTo(new BigDecimal("720.00")));
            assertEquals(Integer.valueOf(40), uninsured.payoutRatioUsed());
            assertEquals(0, uninsured.calculation().payableAmount().compareTo(new BigDecimal("360.00")));
        }

        @Test
        @DisplayName("AC-03 反向：配置社保分档而缺身份 → 显式拒绝，不得默认按高档赔付")
        void shouldRejectWhenIdentityMissing() {
            ClaimPayoutRule rule = socialSecurityRule();

            DomainException ex = assertThrows(DomainException.class,
                    () -> service.adjust(new ReimbursementAdjustmentRequest(
                            new BigDecimal("1000.00"), rule, null)));
            assertEquals(ClaimErrorCode.CLAIM_SOCIAL_SECURITY_REQUIRED, ex.getErrorCode());
        }

        @Test
        @DisplayName("AC-03 反向：空白身份同样显式拒绝（fail-closed）")
        void shouldRejectWhenIdentityBlank() {
            ClaimPayoutRule rule = socialSecurityRule();

            DomainException ex = assertThrows(DomainException.class,
                    () -> service.adjust(new ReimbursementAdjustmentRequest(
                            new BigDecimal("1000.00"), rule, null, "  ")));
            assertEquals(ClaimErrorCode.CLAIM_SOCIAL_SECURITY_REQUIRED, ex.getErrorCode());
        }

        @Test
        @DisplayName("身份未配置档位 → 显式拒绝，不得回落基础比例或非定点档")
        void shouldRejectWhenIdentityHasNoRatio() {
            ClaimPayoutRule rule = socialSecurityRule();

            DomainException ex = assertThrows(DomainException.class,
                    () -> service.adjust(new ReimbursementAdjustmentRequest(
                            new BigDecimal("1000.00"), rule, null, "UNKNOWN_IDENTITY")));
            assertEquals(ClaimErrorCode.CLAIM_CONFIG_INVALID, ex.getErrorCode());
        }

        @Test
        @DisplayName("配置社保分档 → 社保维度为唯一裁决（R-2 前暂定口径），定点台账比例不参与")
        void shouldLetSocialSecurityOverrideDesignatedRatio() {
            ClaimPayoutRule rule = socialSecurityRule();
            ClaimHospitalNetwork hospital = designatedHospital(90);

            ReimbursementAdjustmentResult result = service.adjust(new ReimbursementAdjustmentRequest(
                    new BigDecimal("1000.00"), rule, hospital, "EMPLOYEE"));

            assertEquals(SettlementChannel.DESIGNATED, result.settlementChannel());
            assertEquals(Integer.valueOf(80), result.payoutRatioUsed());
        }

        @Test
        @DisplayName("未配置社保分档 → 携带身份也不改变既有定点/非定点裁决（向后兼容）")
        void shouldIgnoreIdentityWhenDimensionNotConfigured() {
            ClaimPayoutRule rule = payoutRule(60, new BigDecimal("100.00"), null, Map.of("NON_DESIGNATED", 40));

            ReimbursementAdjustmentResult result = service.adjust(new ReimbursementAdjustmentRequest(
                    new BigDecimal("1000.00"), rule, null, "EMPLOYEE"));

            assertEquals(SettlementChannel.NON_DESIGNATED, result.settlementChannel());
            assertEquals(Integer.valueOf(40), result.payoutRatioUsed());
        }
    }

    @Nested
    @DisplayName("医院等级选档裁决（G12/g12-04、AC-05）")
    class HospitalTierTest {

        /** 等级分档：二级 70% / 三级 50%（基础比例 60，用于对照「不得静默回落」） */
        private ClaimPayoutRule tierRule() {
            return payoutRule(60, BigDecimal.ZERO, null, Map.of("二级", 70, "三级", 50));
        }

        @Test
        @DisplayName("AC-05 正向：同一合规费用下，二级与三级医院配置不同比例 → 赔付额不同")
        void shouldPayDifferentAmountsAcrossHospitalLevels() {
            ClaimPayoutRule rule = tierRule();
            BigDecimal expense = new BigDecimal("1000.00");

            ReimbursementAdjustmentResult second = service.adjust(new ReimbursementAdjustmentRequest(
                    expense, rule, designatedHospitalOfLevel("二级", null)));
            ReimbursementAdjustmentResult third = service.adjust(new ReimbursementAdjustmentRequest(
                    expense, rule, designatedHospitalOfLevel("三级", null)));

            assertEquals(Integer.valueOf(70), second.payoutRatioUsed());
            assertEquals(0, second.calculation().payableAmount().compareTo(new BigDecimal("700.00")));
            assertEquals(Integer.valueOf(50), third.payoutRatioUsed());
            assertEquals(0, third.calculation().payableAmount().compareTo(new BigDecimal("500.00")));
        }

        @Test
        @DisplayName("AC-05 反向：规则配了等级分档而该等级无档位 → 显式拒绝，不得静默回落基础比例")
        void shouldRejectWhenHospitalLevelNotConfigured() {
            ClaimPayoutRule rule = tierRule();

            DomainException ex = assertThrows(DomainException.class,
                    () -> service.adjust(new ReimbursementAdjustmentRequest(
                            new BigDecimal("1000.00"), rule, designatedHospitalOfLevel("一级", null))));
            assertEquals(ClaimErrorCode.CLAIM_CONFIG_INVALID, ex.getErrorCode());
            assertTrue(ex.getMessage().contains("医院等级未配置"));
        }

        @Test
        @DisplayName("AC-05 反向：出险医院未登记等级而规则配了等级分档 → 显式拒绝")
        void shouldRejectWhenHospitalLevelBlank() {
            ClaimPayoutRule rule = tierRule();

            DomainException ex = assertThrows(DomainException.class,
                    () -> service.adjust(new ReimbursementAdjustmentRequest(
                            new BigDecimal("1000.00"), rule, designatedHospitalOfLevel("  ", null))));
            assertEquals(ClaimErrorCode.CLAIM_CONFIG_INVALID, ex.getErrorCode());
        }

        @Test
        @DisplayName("台账赔付比例优先于等级分档（个体配置 > 批量默认）")
        void shouldPreferHospitalOwnRatioOverTierRatio() {
            ClaimPayoutRule rule = tierRule();
            ClaimHospitalNetwork hospital = designatedHospitalOfLevel("二级", 90);

            ReimbursementAdjustmentResult result = service.adjust(new ReimbursementAdjustmentRequest(
                    new BigDecimal("1000.00"), rule, hospital));

            assertEquals(Integer.valueOf(90), result.payoutRatioUsed());
        }

        @Test
        @DisplayName("规则未配等级分档 → 等级档不参与，回落基础比例（向后兼容）")
        void shouldFallbackToBaseRatioWhenTierDimensionNotConfigured() {
            ClaimPayoutRule rule = payoutRule(60, BigDecimal.ZERO, null, null);

            ReimbursementAdjustmentResult result = service.adjust(new ReimbursementAdjustmentRequest(
                    new BigDecimal("1000.00"), rule, designatedHospitalOfLevel("三级", null)));

            assertEquals(Integer.valueOf(60), result.payoutRatioUsed());
        }

        @Test
        @DisplayName("非定点路径 NON_DESIGNATED 语义不变（等级档不参与非定点）")
        void shouldKeepNonDesignatedSemanticsUnchanged() {
            ClaimPayoutRule rule = payoutRule(60, BigDecimal.ZERO, null,
                    Map.of("NON_DESIGNATED", 40, "二级", 70));

            ReimbursementAdjustmentResult result = service.adjust(new ReimbursementAdjustmentRequest(
                    new BigDecimal("1000.00"), rule, null));

            assertEquals(SettlementChannel.NON_DESIGNATED, result.settlementChannel());
            assertEquals(Integer.valueOf(40), result.payoutRatioUsed());
        }

        @Test
        @DisplayName("社保分档与等级分档同时配置 → 社保维度唯一裁决，等级档不参与（R-2 前暂定口径）")
        void shouldLetSocialSecurityOverrideHospitalTierRatio() {
            ClaimPayoutRule rule = ClaimPayoutRule.create("rule-1", TENANT_ID, "MEDICAL", "MEDICAL_REIMBURSE",
                    BigDecimal.ZERO, 60, null, null, Map.of("二级", 70),
                    Map.of("EMPLOYEE", 80), List.of());

            ReimbursementAdjustmentResult result = service.adjust(new ReimbursementAdjustmentRequest(
                    new BigDecimal("1000.00"), rule, designatedHospitalOfLevel("二级", null), "EMPLOYEE"));

            assertEquals(Integer.valueOf(80), result.payoutRatioUsed());
        }
    }

    @Nested
    @DisplayName("理算公式")
    class FormulaTest {

        @Test
        @DisplayName("费用低于免赔额 → 应付金额为 0")
        void shouldPayZeroWhenBelowDeductible() {
            ClaimPayoutRule rule = payoutRule(80, new BigDecimal("500.00"), null, null);

            ReimbursementAdjustmentResult result = service.adjust(new ReimbursementAdjustmentRequest(
                    new BigDecimal("400.00"), rule, null));

            assertEquals(0, result.calculation().payableAmount().compareTo(BigDecimal.ZERO));
        }

        @Test
        @DisplayName("超单次限额 → 封顶且标记限额截断")
        void shouldCapByPerClaimLimit() {
            ClaimPayoutRule rule = payoutRule(80, BigDecimal.ZERO, new BigDecimal("500.00"),
                    Map.of("NON_DESIGNATED", 80));

            ReimbursementAdjustmentResult result = service.adjust(new ReimbursementAdjustmentRequest(
                    new BigDecimal("1000.00"), rule, null));

            assertEquals(0, result.calculation().payableAmount().compareTo(new BigDecimal("500.00")));
            assertTrue(result.calculation().cappedByLimit());
        }
    }

    @Nested
    @DisplayName("理算值对象参数校验")
    class CalculationValidationTest {

        @Test
        @DisplayName("合规费用非正数 → 拒绝")
        void shouldRejectNonPositiveExpense() {
            DomainException ex = assertThrows(DomainException.class,
                    () -> ReimbursementCalculation.of(new BigDecimal("0.00"), BigDecimal.ZERO, 80, null));
            assertTrue(ex.getMessage().contains("合规费用"));
        }

        @Test
        @DisplayName("赔付比例越界 → 拒绝")
        void shouldRejectRatioOutOfRange() {
            DomainException ex = assertThrows(DomainException.class,
                    () -> ReimbursementCalculation.of(new BigDecimal("100.00"), BigDecimal.ZERO, 101, null));
            assertTrue(ex.getMessage().contains("赔付比例"));
        }

        @Test
        @DisplayName("免赔额大于单次限额 → 拒绝")
        void shouldRejectDeductibleExceedsLimit() {
            DomainException ex = assertThrows(DomainException.class,
                    () -> ReimbursementCalculation.of(new BigDecimal("100.00"), new BigDecimal("600.00"),
                            80, new BigDecimal("500.00")));
            assertTrue(ex.getMessage().contains("免赔额"));
        }
    }

    @Nested
    @DisplayName("年度封顶（G12/g12-04b、AC-04）")
    class AnnualLimitTest {

        /** 年度限额 1200、免赔 100、非定点档 40% 的赔付规则 */
        private ClaimPayoutRule annualRule() {
            return ClaimPayoutRule.create("rule-1", TENANT_ID, "PET", "PET_MEDICAL",
                    new BigDecimal("100.00"), 60, null, new BigDecimal("1200.00"),
                    Map.of("NON_DESIGNATED", 40), null, List.of());
        }

        @Test
        @DisplayName("AC-04 正向：规则配年额 + 入参带累计 → 应付压减至年度剩余（公式 360，剩余 200）")
        void shouldCapPayableByAnnualRemaining() {
            ReimbursementAdjustmentResult result = service.adjust(new ReimbursementAdjustmentRequest(
                    new BigDecimal("1000.00"), annualRule(), null, null, new BigDecimal("1000.00")));

            // 单次公式 (1000−100)×40% = 360.00；年度剩余 1200−1000 = 200 ⇒ 压减至 200.00
            assertEquals(0, result.calculation().payableAmount().compareTo(new BigDecimal("200.00")));
            assertTrue(result.calculation().cappedByAnnualLimit());
        }

        @Test
        @DisplayName("配了年额但入参无累计（无案件上下文）→ 不压减，保持单次公式 360.00")
        void shouldNotCapWhenAccumulatedMissing() {
            ReimbursementAdjustmentResult result = service.adjust(new ReimbursementAdjustmentRequest(
                    new BigDecimal("1000.00"), annualRule(), null));

            assertEquals(0, result.calculation().payableAmount().compareTo(new BigDecimal("360.00")));
            assertFalse(result.calculation().cappedByAnnualLimit());
        }

        @Test
        @DisplayName("入参带累计但规则未配年额 → 不压减（AND 语义，年度限额权威在规则配置）")
        void shouldNotCapWhenRuleHasNoAnnualLimit() {
            ClaimPayoutRule rule = payoutRule(60, new BigDecimal("100.00"), null,
                    Map.of("NON_DESIGNATED", 40));

            ReimbursementAdjustmentResult result = service.adjust(new ReimbursementAdjustmentRequest(
                    new BigDecimal("1000.00"), rule, null, null, new BigDecimal("99999.00")));

            assertEquals(0, result.calculation().payableAmount().compareTo(new BigDecimal("360.00")));
            assertFalse(result.calculation().cappedByAnnualLimit());
        }
    }
}
