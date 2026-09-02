package com.titanium.claim.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.titanium.claim.common.exception.BusinessException;
import com.titanium.claim.service.impl.ReimbursementAdjustmentServiceImpl;
import com.titanium.claim.valueobject.ReimbursementAdjustmentRequest;
import com.titanium.claim.valueobject.ReimbursementAdjustmentRequest.ReimbursementAdjustmentResult;
import com.titanium.claim.valueobject.ReimbursementCalculation;

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
                deductible, payoutRatio, perClaimLimit, null, hospitalTierRatios, List.of());
    }

    private ClaimHospitalNetwork designatedHospital(Integer payoutRatio) {
        return ClaimHospitalNetwork.create("hosp-1", TENANT_ID, "爱宠宠物医院", "二级",
                HospitalAgreementStatus.ACTIVE, payoutRatio, true, "朝阳区", "13800000000");
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

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.adjust(new ReimbursementAdjustmentRequest(
                            new BigDecimal("1000.00"), rule, null)));
            assertTrue(ex.getMessage().contains("赔付比例"));
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
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> ReimbursementCalculation.of(new BigDecimal("0.00"), BigDecimal.ZERO, 80, null));
            assertTrue(ex.getMessage().contains("合规费用"));
        }

        @Test
        @DisplayName("赔付比例越界 → 拒绝")
        void shouldRejectRatioOutOfRange() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> ReimbursementCalculation.of(new BigDecimal("100.00"), BigDecimal.ZERO, 101, null));
            assertTrue(ex.getMessage().contains("赔付比例"));
        }

        @Test
        @DisplayName("免赔额大于单次限额 → 拒绝")
        void shouldRejectDeductibleExceedsLimit() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> ReimbursementCalculation.of(new BigDecimal("100.00"), new BigDecimal("600.00"),
                            80, new BigDecimal("500.00")));
            assertTrue(ex.getMessage().contains("免赔额"));
        }
    }
}
