package com.titanium.claim.valueobject;

import java.math.BigDecimal;

import com.titanium.metadata.enums.claim.ClaimEnum;

/**
 * 理赔核赔结论值对象
 * <p>
 * 封装核赔阶段的赔付决定：核定赔付金额、给付方式、收款方。 用于 APPROVED → PAID 的核赔结算环节，是触发支付域 CLAIM_PAYOUT 的数据来源。
 * </p>
 *
 * @param settledAmount 核定赔付金额
 * @param payoutMethod  给付方式（BANK_TRANSFER/CASH 等）
 * @param payeeAccount  收款方账户/标识
 * @param conclusion    核赔意见
 */
public record ClaimSettlement(
        BigDecimal settledAmount,
        ClaimEnum.PayoutMethod payoutMethod,
        String payeeAccount,
        String conclusion) {

    public ClaimSettlement {
        if (settledAmount == null || settledAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("核定赔付金额必须大于0");
        }
    }

    /**
     * 工厂方法
     *
     * @param settledAmount 核定赔付金额
     * @param payoutMethod 给付方式
     * @param payeeAccount 收款方
     * @param conclusion 核赔意见
     * @return 核赔结论值对象
     */
    public static ClaimSettlement of(BigDecimal settledAmount, ClaimEnum.PayoutMethod payoutMethod, String payeeAccount,
                                     String conclusion) {
        return new ClaimSettlement(settledAmount, payoutMethod, payeeAccount, conclusion);
    }
}
