package com.titanium.claim.valueobject;

import java.math.BigDecimal;

import com.titanium.metadata.enums.claim.ClaimEnum;
import lombok.Getter;

/**
 * 理赔核赔结论值对象
 * <p>
 * 封装核赔阶段的赔付决定：核定赔付金额、给付方式、收款方。 用于 APPROVED → PAID 的核赔结算环节，是触发支付域 CLAIM_PAYOUT 的数据来源。
 * </p>
 */
@Getter
public class ClaimSettlement {

    /** 核定赔付金额 */
    private final BigDecimal settledAmount;

    /** 给付方式（BANK_TRANSFER/CASH 等） */
    private final ClaimEnum.PayoutMethod payoutMethod;

    /** 收款方账户/标识 */
    private final String payeeAccount;

    /** 核赔意见 */
    private final String conclusion;

    private ClaimSettlement(BigDecimal settledAmount, ClaimEnum.PayoutMethod payoutMethod, String payeeAccount, String conclusion) {
        if (settledAmount == null || settledAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("核定赔付金额必须大于0");
        }
        this.settledAmount = settledAmount;
        this.payoutMethod = payoutMethod;
        this.payeeAccount = payeeAccount;
        this.conclusion = conclusion;
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
