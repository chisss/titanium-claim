package com.titanium.claim.valueobject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.claim.common.enums.BenefitSource;
import com.titanium.claim.common.exception.BenefitCalculationException;
import com.titanium.metadata.errorcode.ClaimErrorCode;

/**
 * 身故给付金核算值对象测试（CLAIM-2 精算规则守护）
 * <p>
 * 验证 {@link BenefitCalculation} 的不变量：给付总额正数、来源规则必携、受益人份额非空、
 * 比例之和等于 1、份额之和等于给付总额（双重守恒）；以及 {@link BenefitCalculation#ofBasicSumInsured}
 * 工厂按比例分配与尾差调整。
 * </p>
 */
class BenefitCalculationTest {

    private static final String B1 = "B-1";
    private static final String B2 = "B-2";
    private static final BigDecimal RATIO_6 = new BigDecimal("0.6");
    private static final BigDecimal RATIO_4 = new BigDecimal("0.4");

    private BenefitCalculation.BeneficiaryShare share(String id, String name, BigDecimal ratio, BigDecimal amount) {
        return new BenefitCalculation.BeneficiaryShare(id, name, ratio, amount);
    }

    @Test
    @DisplayName("构造成功：比例之和为1且份额之和等于给付总额")
    void shouldConstructWhenInvariantsHold() {
        BenefitCalculation calc = new BenefitCalculation(new BigDecimal("500000"),
                List.of(share(B1, "配偶", RATIO_6, new BigDecimal("300000")),
                        share(B2, "子女", RATIO_4, new BigDecimal("200000"))),
                BenefitSource.BASIC_SUM_INSURED);

        assertEquals(new BigDecimal("500000"), calc.totalBenefit());
        assertEquals(BenefitSource.BASIC_SUM_INSURED, calc.source());
        assertEquals(2, calc.shares().size());
        assertEquals(new BigDecimal("300000"), calc.payoutOf(B1));
        assertEquals(new BigDecimal("200000"), calc.payoutOf(B2));
        assertEquals(BigDecimal.ZERO, calc.payoutOf("B-999"));
    }

    @Test
    @DisplayName("给付总额非正数抛金额无效错误码")
    void shouldRejectNonPositiveTotal() {
        BenefitCalculationException ex = assertThrows(BenefitCalculationException.class,
                () -> new BenefitCalculation(BigDecimal.ZERO,
                        List.of(share(B1, "配偶", BigDecimal.ONE, BigDecimal.ZERO)),
                        BenefitSource.BASIC_SUM_INSURED));
        assertEquals(ClaimErrorCode.CLAIM_BENEFIT_AMOUNT_INVALID.getCode(), ex.getErrorCode());
    }

    @Test
    @DisplayName("金额来源规则缺失抛金额无效错误码")
    void shouldRejectMissingSource() {
        BenefitCalculationException ex = assertThrows(BenefitCalculationException.class,
                () -> new BenefitCalculation(new BigDecimal("500000"),
                        List.of(share(B1, "配偶", BigDecimal.ONE, new BigDecimal("500000"))), null));
        assertEquals(ClaimErrorCode.CLAIM_BENEFIT_AMOUNT_INVALID.getCode(), ex.getErrorCode());
    }

    @Test
    @DisplayName("受益人份额缺失抛份额缺失错误码")
    void shouldRejectMissingShares() {
        BenefitCalculationException ex = assertThrows(BenefitCalculationException.class,
                () -> new BenefitCalculation(new BigDecimal("500000"), List.of(), BenefitSource.BASIC_SUM_INSURED));
        assertEquals(ClaimErrorCode.CLAIM_BENEFIT_SHARE_MISSING.getCode(), ex.getErrorCode());
    }

    @Test
    @DisplayName("受益人比例之和不为1抛份额不匹配错误码")
    void shouldRejectRatioSumNotOne() {
        BenefitCalculationException ex = assertThrows(BenefitCalculationException.class,
                () -> new BenefitCalculation(new BigDecimal("500000"),
                        List.of(share(B1, "配偶", RATIO_6, new BigDecimal("300000")),
                                share(B2, "子女", RATIO_6, new BigDecimal("300000"))),
                        BenefitSource.BASIC_SUM_INSURED));
        assertEquals(ClaimErrorCode.CLAIM_BENEFIT_SHARE_MISMATCH.getCode(), ex.getErrorCode());
    }

    @Test
    @DisplayName("受益人份额之和与给付总额不一致抛份额不匹配错误码")
    void shouldRejectAmountSumMismatch() {
        BenefitCalculationException ex = assertThrows(BenefitCalculationException.class,
                () -> new BenefitCalculation(new BigDecimal("500000"),
                        List.of(share(B1, "配偶", RATIO_6, new BigDecimal("300000")),
                                share(B2, "子女", RATIO_4, new BigDecimal("199999"))),
                        BenefitSource.BASIC_SUM_INSURED));
        assertEquals(ClaimErrorCode.CLAIM_BENEFIT_SHARE_MISMATCH.getCode(), ex.getErrorCode());
    }

    @Test
    @DisplayName("受益比例非(0,1]区间抛份额不匹配错误码")
    void shouldRejectInvalidRatio() {
        assertThrows(BenefitCalculationException.class,
                () -> new BenefitCalculation.BeneficiaryShare(B1, "配偶", BigDecimal.ZERO, BigDecimal.ZERO));
        assertThrows(BenefitCalculationException.class,
                () -> new BenefitCalculation.BeneficiaryShare(B1, "配偶", new BigDecimal("1.2"), BigDecimal.ZERO));
        assertThrows(BenefitCalculationException.class,
                () -> new BenefitCalculation.BeneficiaryShare(B1, "配偶", null, BigDecimal.ZERO));
    }

    @Test
    @DisplayName("工厂按比例分配：0.6/0.4 无尾差")
    void shouldAllocateByRatioExactly() {
        BenefitCalculation calc = BenefitCalculation.ofBasicSumInsured(new BigDecimal("500000"),
                List.of(new BenefitCalculation.BeneficiaryShareSpec(B1, "配偶", RATIO_6),
                        new BenefitCalculation.BeneficiaryShareSpec(B2, "子女", RATIO_4)));

        assertEquals(0, calc.payoutOf(B1).compareTo(new BigDecimal("300000")));
        assertEquals(0, calc.payoutOf(B2).compareTo(new BigDecimal("200000")));
        assertEquals(new BigDecimal("500000"), calc.totalBenefit());
    }

    @Test
    @DisplayName("工厂尾差调整：三分等分时最后一位受益人吸收尾差且总额守恒")
    void shouldAdjustRoundingTailToLastBeneficiary() {
        BenefitCalculation calc = BenefitCalculation.ofBasicSumInsured(new BigDecimal("10000"),
                List.of(new BenefitCalculation.BeneficiaryShareSpec("B-1", "甲", new BigDecimal("0.34")),
                        new BenefitCalculation.BeneficiaryShareSpec("B-2", "乙", new BigDecimal("0.33")),
                        new BenefitCalculation.BeneficiaryShareSpec("B-3", "丙", new BigDecimal("0.33"))));

        // 0.34*10000=3400 精确；0.33*10000=3300 精确，本例无尾差时仍验证守恒
        BigDecimal sum = calc.shares().stream()
                .map(BenefitCalculation.BeneficiaryShare::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, sum.compareTo(new BigDecimal("10000")));
        assertEquals(3, calc.shares().size());
    }

    @Test
    @DisplayName("工厂尾差调整：1/3 等分产生循环小数，最后一位吸收尾差")
    void shouldAdjustRepeatingDecimalTail() {
        BigDecimal oneThird = new BigDecimal("0.3333");
        BenefitCalculation calc = BenefitCalculation.ofBasicSumInsured(new BigDecimal("10000"),
                List.of(new BenefitCalculation.BeneficiaryShareSpec("B-1", "甲", oneThird),
                        new BenefitCalculation.BeneficiaryShareSpec("B-2", "乙", oneThird),
                        new BenefitCalculation.BeneficiaryShareSpec("B-3", "丙", new BigDecimal("0.3334"))));

        // 前两位四舍五入各 3333.00，最后一位吸收尾差得 3334.00
        assertEquals(new BigDecimal("3333.00"), calc.payoutOf("B-1"));
        assertEquals(new BigDecimal("3333.00"), calc.payoutOf("B-2"));
        assertEquals(new BigDecimal("3334.00"), calc.payoutOf("B-3"));
        BigDecimal sum = calc.shares().stream()
                .map(BenefitCalculation.BeneficiaryShare::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, sum.compareTo(new BigDecimal("10000")));
        assertTrue(sum.compareTo(calc.totalBenefit()) == 0);
    }

    @Test
    @DisplayName("工厂空份额规格抛份额缺失错误码")
    void shouldRejectEmptySpecs() {
        BenefitCalculationException ex = assertThrows(BenefitCalculationException.class,
                () -> BenefitCalculation.ofBasicSumInsured(new BigDecimal("500000"), List.of()));
        assertEquals(ClaimErrorCode.CLAIM_BENEFIT_SHARE_MISSING.getCode(), ex.getErrorCode());
    }
}
