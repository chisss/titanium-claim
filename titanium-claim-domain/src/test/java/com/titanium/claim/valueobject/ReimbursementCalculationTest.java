package com.titanium.claim.valueobject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.errorcode.ClaimErrorCode;
import com.titanium.metadata.exception.DomainException;

/**
 * 报销理算产物值对象的量纲不变量测试（G12/g12-05）
 * <p>
 * 🔴 守的是<b>量纲口径</b>而非公式本身：理赔报销链路的赔付比例一律 <b>0-100 百分比整数</b>
 * （口径权威声明见 {@link com.titanium.claim.aggregate.ClaimPayoutRule} 类头「量纲口径」段），
 * 本值对象的 {@code of()} 是该口径的<b>唯一换算落地处</b>（÷100 HALF_UP 两位小数）。
 * 与条款域 {@code Reimbursement.reimbursementRatio}（<b>0-1 小数</b>）口径不同，禁止隐式换算。
 * </p>
 * <p>
 * 关键区分力：{@code ratio=1} 须表示 <b>1%</b>（8.00）而非 100%（800.00）——这一条把 0-100 与
 * 0-1 两套口径在行为面钉死；若有人按 0-1 小数口径传值或误删 ÷100，本测试必红。
 * </p>
 *
 * @author wei.sun
 * @since 2026/10/10
 */
class ReimbursementCalculationTest {

    @Test
    @DisplayName("比例按 0-100 百分比口径换算：50 表示 50%（800 × 50% = 400.00）")
    void shouldApplyRatioAsPercentageNotFraction() {
        ReimbursementCalculation calc = ReimbursementCalculation.of(
                new BigDecimal("800"), null, 50, null);
        assertEquals(0, calc.payableAmount().compareTo(new BigDecimal("400.00")),
                "比例 50 须按 50% 换算（400.00）；得 40000 说明漏除 100，得 4 说明除 100 两次");
    }

    @Test
    @DisplayName("量纲分界：比例 1 表示 1%（8.00），不是 0-1 口径的 100%")
    void shouldTreatRatioOneAsOnePercent() {
        ReimbursementCalculation calc = ReimbursementCalculation.of(
                new BigDecimal("800"), null, 1, null);
        assertEquals(0, calc.payableAmount().compareTo(new BigDecimal("8.00")),
                "0-100 口径下 1 表示 1%；若为 800.00 则说明被按 0-1 小数点口径消费");
    }

    @Test
    @DisplayName("比例 100 表示全额（未超限额时 800.00 原样赔付）")
    void shouldReachFullAmountAtRatioHundred() {
        ReimbursementCalculation calc = ReimbursementCalculation.of(
                new BigDecimal("800"), null, 100, null);
        assertEquals(0, calc.payableAmount().compareTo(new BigDecimal("800.00")));
        assertFalse(calc.cappedByLimit(), "限额未配置时绝不应报告截断");
    }

    @Test
    @DisplayName("免赔额不小于合规费用时应付为 0（不做负数赔付）")
    void shouldPayZeroWhenDeductibleCoversExpense() {
        ReimbursementCalculation calc = ReimbursementCalculation.of(
                new BigDecimal("800"), new BigDecimal("800"), 100, null);
        assertEquals(0, calc.payableAmount().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("比例越界拒绝（101 / -1 双边界，CLAIM_AMOUNT_ERROR）")
    void shouldRejectRatioOutOfPercentageRange() {
        DomainException above = assertThrows(DomainException.class,
                () -> ReimbursementCalculation.of(new BigDecimal("800"), null, 101, null));
        assertEquals(ClaimErrorCode.CLAIM_AMOUNT_ERROR, above.getErrorCode(),
                "101 越出 0-100 口径上界，须拒绝（该拒绝同时是 0-1 口径误传的哨兵）");

        DomainException below = assertThrows(DomainException.class,
                () -> ReimbursementCalculation.of(new BigDecimal("800"), null, -1, null));
        assertEquals(ClaimErrorCode.CLAIM_AMOUNT_ERROR, below.getErrorCode());
    }

    @Test
    @DisplayName("单次限额截断：应付=限额且 cappedByLimit=true（1000 × 80% = 800 > 500）")
    void shouldCapByPerClaimLimit() {
        ReimbursementCalculation calc = ReimbursementCalculation.of(
                new BigDecimal("1000"), null, 80, new BigDecimal("500"));
        assertEquals(0, calc.payableAmount().compareTo(new BigDecimal("500")));
        assertTrue(calc.cappedByLimit());
    }

    @Test
    @DisplayName("恰好等于限额但未被截断时 cappedByLimit=false（等于 ≠ 被截断）")
    void shouldNotReportCapWhenAmountMerelyEqualsLimit() {
        // 1000 × 20% = 200，恰好等于限额 200：是「算出来正好 200」而非「被 200 截断」
        ReimbursementCalculation calc = ReimbursementCalculation.of(
                new BigDecimal("1000"), null, 20, new BigDecimal("200"));
        assertEquals(0, calc.payableAmount().compareTo(new BigDecimal("200")));
        assertFalse(calc.cappedByLimit(),
                "「恰好等于」不构成截断——判定须比较截断前的原始金额（×100 同量纲比较）");
    }
}
