package com.titanium.claim.valueobject;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.titanium.metadata.errorcode.ClaimErrorCode;
import com.titanium.metadata.exception.DomainException;

/**
 * 报销理算产物值对象（健康险/宠物险报销型理赔的金额精算，产品文档 §2.7 理算公式）
 * <p>
 * 充血不可变值对象：理算公式 {@code min((合规费用 − 免赔额) × 赔付比例, 单次限额)} 的参数
 * （免赔额/比例/限额）强制来自赔付规则配置（{@link com.titanium.claim.aggregate.ClaimPayoutRule}），
 * 杜绝透传金额；紧凑构造器校验参数合法性，理算金额 HALF_UP 保留两位小数。
 * </p>
 * <p>
 * 🔴 <b>量纲口径（G12/g12-05）</b>：{@code payoutRatio} 为 <b>0-100 百分比整数</b>（构造器校验区间
 * [0,100]），{@code of()} 是该口径的<b>唯一换算落地处</b>（÷100、HALF_UP 两位小数）——量纲不变量由
 * {@code ReimbursementCalculationTest} 钉住。口径权威声明与「与条款域
 * {@code Reimbursement.reimbursementRatio} 0-1 小数口径禁止隐式换算」的纪律见
 * {@link com.titanium.claim.aggregate.ClaimPayoutRule} 类头「量纲口径」段。
 * </p>
 *
 * <p>
 * <b>年度封顶（G12/g12-04b、AC-04）</b>：{@code of()} 六参重载在单次公式之上叠加年度压减
 * {@code 应付 = min(单次公式值, max(0, 年度限额 − 年度累计))}。年度累计口径（自然年 / 保单内标的 /
 * 出险日锚定 / 只计 {@code PAID}+{@code CLOSED} 终态）由取数层
 * （{@code ReimbursementAdjustmentQueryService}）裁决并作为入参传入，本值对象<b>不做取数</b>。
 * </p>
 *
 * @param eligibleExpense 合规费用（核定后的可赔费用，须大于 0）
 * @param deductible      免赔额（元，可为空=0）
 * @param payoutRatio     赔付比例（0-100 百分比，不可空）
 * @param perClaimLimit   单次限额（元，可为空=不限）
 * @param annualLimit     年度限额（元，可为空=不限；G12/g12-04b）
 * @param annualAccumulated 本年度累计已赔付金额（元，可为空=无年度上下文，不压减；G12/g12-04b）
 * @param payableAmount   理算应付金额（公式产物，含单次限额与年度封顶双重截断后的最终值）
 */
public record ReimbursementCalculation(
        BigDecimal eligibleExpense,
        BigDecimal deductible,
        Integer payoutRatio,
        BigDecimal perClaimLimit,
        BigDecimal annualLimit,
        BigDecimal annualAccumulated,
        BigDecimal payableAmount) {

    public ReimbursementCalculation {
        if (eligibleExpense == null || eligibleExpense.compareTo(BigDecimal.ZERO) <= 0) {
            throw new DomainException(ClaimErrorCode.CLAIM_AMOUNT_ERROR, "合规费用必须大于0");
        }
        if (payoutRatio == null || payoutRatio < 0 || payoutRatio > 100) {
            throw new DomainException(ClaimErrorCode.CLAIM_AMOUNT_ERROR,
                    "赔付比例必须在0-100之间: " + payoutRatio);
        }
        if (deductible != null && deductible.compareTo(BigDecimal.ZERO) < 0) {
            throw new DomainException(ClaimErrorCode.CLAIM_AMOUNT_ERROR, "免赔额不能为负数");
        }
        if (deductible != null && perClaimLimit != null && deductible.compareTo(perClaimLimit) > 0) {
            throw new DomainException(ClaimErrorCode.CLAIM_AMOUNT_ERROR, "免赔额不能大于单次限额");
        }
        if (annualLimit != null && annualLimit.compareTo(BigDecimal.ZERO) < 0) {
            throw new DomainException(ClaimErrorCode.CLAIM_AMOUNT_ERROR, "年度限额不能为负数");
        }
        if (annualAccumulated != null && annualAccumulated.compareTo(BigDecimal.ZERO) < 0) {
            throw new DomainException(ClaimErrorCode.CLAIM_AMOUNT_ERROR, "年度累计赔付金额不能为负数");
        }
    }

    /**
     * 理算工厂（无年度上下文的向后兼容重载）：按公式
     * {@code min((合规费用 − 免赔额) × 比例, 单次限额)} 计算应付金额。
     *
     * @param eligibleExpense 合规费用
     * @param deductible      免赔额（可为空=0）
     * @param payoutRatio     赔付比例（0-100）
     * @param perClaimLimit   单次限额（可为空=不限）
     * @return 理算产物
     */
    public static ReimbursementCalculation of(BigDecimal eligibleExpense, BigDecimal deductible,
                                              Integer payoutRatio, BigDecimal perClaimLimit) {
        return of(eligibleExpense, deductible, payoutRatio, perClaimLimit, null, null);
    }

    /**
     * 理算工厂（G12/g12-04b 年度封顶）：先按单次公式计算，再按年度剩余额度压减——
     * {@code 应付 = min( min((合规费用 − 免赔额) × 比例, 单次限额), max(0, 年度限额 − 年度累计) )}。
     * <p>
     * 🔴 年度口径（自然年 / 保单内标的 / 出险日锚定 / 只计已结算终态）由取数层裁决，本值对象只承载
     * 压减公式；{@code annualLimit} 或 {@code annualAccumulated} 为空（规则未配年度限额 / 无案件上下文）
     * 时<b>不压减</b>（单次公式口径，向后兼容），压减与否由 {@link #cappedByAnnualLimit()} 显式暴露。
     * </p>
     *
     * @param eligibleExpense   合规费用
     * @param deductible        免赔额（可为空=0）
     * @param payoutRatio       赔付比例（0-100）
     * @param perClaimLimit     单次限额（可为空=不限）
     * @param annualLimit       年度限额（可为空=不限）
     * @param annualAccumulated 本年度累计已赔付（可为空=无年度上下文，不压减）
     * @return 理算产物（{@code payableAmount} 为双重截断后的最终值）
     */
    public static ReimbursementCalculation of(BigDecimal eligibleExpense, BigDecimal deductible,
                                              Integer payoutRatio, BigDecimal perClaimLimit,
                                              BigDecimal annualLimit, BigDecimal annualAccumulated) {
        BigDecimal payable = basePayable(eligibleExpense, deductible, payoutRatio, perClaimLimit);
        if (annualLimit != null && annualAccumulated != null) {
            BigDecimal remaining = annualLimit.subtract(annualAccumulated).max(BigDecimal.ZERO);
            if (payable.compareTo(remaining) > 0) {
                payable = remaining;
            }
        }
        return new ReimbursementCalculation(eligibleExpense, deductible, payoutRatio, perClaimLimit,
                annualLimit, annualAccumulated, payable);
    }

    /**
     * 单次公式基础应付金额：{@code min((合规费用 − 免赔额) × 比例, 单次限额)}（不含年度封顶）。
     */
    private static BigDecimal basePayable(BigDecimal eligibleExpense, BigDecimal deductible,
                                          Integer payoutRatio, BigDecimal perClaimLimit) {
        BigDecimal deductibleOrZero = deductible == null ? BigDecimal.ZERO : deductible;
        BigDecimal afterDeductible = eligibleExpense.subtract(deductibleOrZero);
        BigDecimal payable = afterDeductible.signum() <= 0 ? BigDecimal.ZERO
                : afterDeductible.multiply(BigDecimal.valueOf(payoutRatio))
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        if (perClaimLimit != null && payable.compareTo(perClaimLimit) > 0) {
            payable = perClaimLimit;
        }
        return payable;
    }

    /**
     * 是否触发单次限额截断（应付金额被限额封顶）
     *
     * @return 触发限额封顶返回 {@code true}
     */
    public boolean cappedByLimit() {
        return perClaimLimit != null && payableAmount.compareTo(perClaimLimit) == 0
                && eligibleExpense.subtract(deductible == null ? BigDecimal.ZERO : deductible)
                        .multiply(BigDecimal.valueOf(payoutRatio))
                        .compareTo(perClaimLimit.multiply(BigDecimal.valueOf(100))) > 0;
    }

    /**
     * 是否触发年度封顶截断（G12/g12-04b、AC-04）
     * <p>
     * 判据与 {@link #cappedByLimit()} 同法：比较<b>截断前</b>的单次公式基础金额与年度剩余额度
     * （{@code max(0, 年度限额 − 年度累计)}），「恰好等于」不构成截断。无年度上下文
     * （{@code annualLimit} 或 {@code annualAccumulated} 为空）恒为 {@code false}。
     * </p>
     *
     * @return 触发年度封顶返回 {@code true}
     */
    public boolean cappedByAnnualLimit() {
        if (annualLimit == null || annualAccumulated == null) {
            return false;
        }
        BigDecimal remaining = annualLimit.subtract(annualAccumulated).max(BigDecimal.ZERO);
        return basePayable(eligibleExpense, deductible, payoutRatio, perClaimLimit).compareTo(remaining) > 0;
    }
}
