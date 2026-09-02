package com.titanium.claim.valueobject;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.titanium.claim.common.exception.BusinessException;
import com.titanium.metadata.errorcode.ClaimErrorCode;

/**
 * 报销理算产物值对象（健康险/宠物险报销型理赔的金额精算，产品文档 §2.7 理算公式）
 * <p>
 * 充血不可变值对象：理算公式 {@code min((合规费用 − 免赔额) × 赔付比例, 单次限额)} 的参数
 * （免赔额/比例/限额）强制来自赔付规则配置（{@link com.titanium.claim.aggregate.ClaimPayoutRule}），
 * 杜绝透传金额；紧凑构造器校验参数合法性，理算金额 HALF_UP 保留两位小数。
 * </p>
 *
 * @param eligibleExpense 合规费用（核定后的可赔费用，须大于 0）
 * @param deductible      免赔额（元，可为空=0）
 * @param payoutRatio     赔付比例（0-100 百分比，不可空）
 * @param perClaimLimit   单次限额（元，可为空=不限）
 * @param payableAmount   理算应付金额（公式产物）
 */
public record ReimbursementCalculation(
        BigDecimal eligibleExpense,
        BigDecimal deductible,
        Integer payoutRatio,
        BigDecimal perClaimLimit,
        BigDecimal payableAmount) {

    public ReimbursementCalculation {
        if (eligibleExpense == null || eligibleExpense.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ClaimErrorCode.CLAIM_AMOUNT_ERROR, "合规费用必须大于0");
        }
        if (payoutRatio == null || payoutRatio < 0 || payoutRatio > 100) {
            throw new BusinessException(ClaimErrorCode.CLAIM_AMOUNT_ERROR,
                    "赔付比例必须在0-100之间: " + payoutRatio);
        }
        if (deductible != null && deductible.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ClaimErrorCode.CLAIM_AMOUNT_ERROR, "免赔额不能为负数");
        }
        if (deductible != null && perClaimLimit != null && deductible.compareTo(perClaimLimit) > 0) {
            throw new BusinessException(ClaimErrorCode.CLAIM_AMOUNT_ERROR, "免赔额不能大于单次限额");
        }
    }

    /**
     * 理算工厂：按公式 {@code min((合规费用 − 免赔额) × 比例, 单次限额)} 计算应付金额。
     *
     * @param eligibleExpense 合规费用
     * @param deductible      免赔额（可为空=0）
     * @param payoutRatio     赔付比例（0-100）
     * @param perClaimLimit   单次限额（可为空=不限）
     * @return 理算产物
     */
    public static ReimbursementCalculation of(BigDecimal eligibleExpense, BigDecimal deductible,
                                              Integer payoutRatio, BigDecimal perClaimLimit) {
        BigDecimal deductibleOrZero = deductible == null ? BigDecimal.ZERO : deductible;
        BigDecimal afterDeductible = eligibleExpense.subtract(deductibleOrZero);
        BigDecimal payable = afterDeductible.signum() <= 0 ? BigDecimal.ZERO
                : afterDeductible.multiply(BigDecimal.valueOf(payoutRatio))
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        if (perClaimLimit != null && payable.compareTo(perClaimLimit) > 0) {
            payable = perClaimLimit;
        }
        return new ReimbursementCalculation(eligibleExpense, deductible, payoutRatio, perClaimLimit, payable);
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
}
