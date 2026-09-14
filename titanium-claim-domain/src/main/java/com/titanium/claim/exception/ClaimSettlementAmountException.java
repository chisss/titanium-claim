package com.titanium.claim.exception;

import java.math.BigDecimal;

import com.titanium.claim.valueobject.ClaimId;
import com.titanium.metadata.errorcode.ClaimErrorCode;
import com.titanium.metadata.exception.DomainException;

/**
 * 核赔结算金额核定异常
 * <p>
 * 核赔结算的核定赔付金额存在**唯一权威来源**：案件已定损时取定损核定额
 * （{@code LossAssessment.payableAmount()} =（定损总金额−残值）×责任比例），调用方不得透传改数；
 * 未定损的案件才由调用方指定金额。本异常覆盖该规则的三类违约：
 * <ul>
 *   <li>无定损记录且未指定金额 —— 结算金额无来源</li>
 *   <li>定损在案但传入金额不等于定损核定额 —— 人工透传改数</li>
 *   <li>定损核定额计算为非正数 —— 定损数据本身不可用于赔付</li>
 * </ul>
 * </p>
 *
 * @author wei.sun
 * @since 2026/9/14
 */
public class ClaimSettlementAmountException extends DomainException {

    private ClaimSettlementAmountException(ClaimErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * 无定损记录且调用方未指定核定赔付金额。
     *
     * @param claimId 理赔案件ID
     * @return 异常
     */
    public static ClaimSettlementAmountException required(ClaimId claimId) {
        return new ClaimSettlementAmountException(ClaimErrorCode.CLAIM_SETTLEMENT_AMOUNT_REQUIRED,
                String.format("理赔案件[%s] 无定损记录，核定赔付金额必须由调用方指定", claimId.value()));
    }

    /**
     * 定损在案，但调用方传入的金额与定损核定额不一致。
     *
     * @param claimId         理赔案件ID
     * @param assessedAmount  定损核定额
     * @param providedAmount  调用方传入金额
     * @return 异常
     */
    public static ClaimSettlementAmountException mismatch(ClaimId claimId, BigDecimal assessedAmount,
                                                          BigDecimal providedAmount) {
        return new ClaimSettlementAmountException(ClaimErrorCode.CLAIM_SETTLEMENT_AMOUNT_MISMATCH,
                String.format("理赔案件[%s] 定损在案，核定赔付金额须等于定损核定额[%s]，实际传入[%s]",
                        claimId.value(), assessedAmount.toPlainString(), providedAmount.toPlainString()));
    }

    /**
     * 定损核定额计算为非正数（定损金额已扣除残值后不足以赔付），不可用于结算。
     *
     * @param claimId        理赔案件ID
     * @param assessedAmount 定损核定额
     * @return 异常
     */
    public static ClaimSettlementAmountException nonPositive(ClaimId claimId, BigDecimal assessedAmount) {
        return new ClaimSettlementAmountException(ClaimErrorCode.CLAIM_AMOUNT_ERROR,
                String.format("理赔案件[%s] 定损核定额[%s]必须大于0，请核对定损金额与残值扣减",
                        claimId.value(), assessedAmount.toPlainString()));
    }
}
