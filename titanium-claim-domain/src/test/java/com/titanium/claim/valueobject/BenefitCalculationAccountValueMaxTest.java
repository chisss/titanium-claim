package com.titanium.claim.valueobject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.claim.common.enums.BenefitSource;

/**
 * 全残给付精算工厂测试（CLAIM-6：max(账户价值, 基本保额)）
 * <p>
 * 验证 {@code ofAccountValueMax}：账户价值高于基本保额取账户价值、低于取基本保额、
 * 未取到（null）回落基本保额；来源规则标记 ACCOUNT_VALUE_MAX；份额守恒与尾差调整
 * 沿用通用分配逻辑（最后一位受益人吸收尾差）。
 * </p>
 */
@DisplayName("全残给付精算")
class BenefitCalculationAccountValueMaxTest {

    private final List<BenefitCalculation.BeneficiaryShareSpec> specs = List.of(
            new BenefitCalculation.BeneficiaryShareSpec("B-1", "配偶", new BigDecimal("0.6")),
            new BenefitCalculation.BeneficiaryShareSpec("B-2", "子女", new BigDecimal("0.4")));

    @Test
    @DisplayName("账户价值高于基本保额 → 取账户价值")
    void shouldUseAccountValueWhenHigher() {
        BenefitCalculation calculation = BenefitCalculation.ofAccountValueMax(new BigDecimal("600000"),
                new BigDecimal("500000"), specs);

        assertEquals(BenefitSource.ACCOUNT_VALUE_MAX, calculation.source());
        assertEquals(0, calculation.totalBenefit().compareTo(new BigDecimal("600000")));
        // 0.6 × 600000 = 360000；尾差由最后一位吸收：600000 − 360000 = 240000
        assertEquals(0, calculation.payoutOf("B-1").compareTo(new BigDecimal("360000.00")));
        assertEquals(0, calculation.payoutOf("B-2").compareTo(new BigDecimal("240000.00")));
    }

    @Test
    @DisplayName("账户价值低于基本保额 → 取基本保额")
    void shouldUseBasicSumInsuredWhenHigher() {
        BenefitCalculation calculation = BenefitCalculation.ofAccountValueMax(new BigDecimal("400000"),
                new BigDecimal("500000"), specs);

        assertEquals(0, calculation.totalBenefit().compareTo(new BigDecimal("500000")));
        assertEquals(0, calculation.payoutOf("B-1").compareTo(new BigDecimal("300000.00")));
        assertEquals(0, calculation.payoutOf("B-2").compareTo(new BigDecimal("200000.00")));
    }

    @Test
    @DisplayName("账户价值未取到（null）→ 回落基本保额（向后兼容）")
    void shouldFallbackToBasicSumInsuredWhenAccountValueMissing() {
        BenefitCalculation calculation = BenefitCalculation.ofAccountValueMax(null, new BigDecimal("500000"), specs);

        assertEquals(0, calculation.totalBenefit().compareTo(new BigDecimal("500000")));
    }

    @Test
    @DisplayName("非整除比例尾差调整：份额之和精确等于给付总额")
    void shouldAdjustTailDifferenceToLastBeneficiary() {
        List<BenefitCalculation.BeneficiaryShareSpec> threeWay = List.of(
                new BenefitCalculation.BeneficiaryShareSpec("B-1", "甲", new BigDecimal("0.33")),
                new BenefitCalculation.BeneficiaryShareSpec("B-2", "乙", new BigDecimal("0.33")),
                new BenefitCalculation.BeneficiaryShareSpec("B-3", "丙", new BigDecimal("0.34")));
        BenefitCalculation calculation = BenefitCalculation.ofAccountValueMax(new BigDecimal("100000"),
                new BigDecimal("80000"), threeWay);

        BigDecimal sum = calculation.shares().stream()
                .map(BenefitCalculation.BeneficiaryShare::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, sum.compareTo(calculation.totalBenefit()));
        assertTrue(calculation.totalBenefit().compareTo(new BigDecimal("80000")) > 0);
    }
}
