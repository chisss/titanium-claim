package com.titanium.claim.command;

import java.math.BigDecimal;

import com.titanium.claim.valueobject.ClaimId;
import com.titanium.metadata.enums.claim.ClaimEnum;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * 核赔结算命令（领域层）
 * <p>
 * 在理赔 APPROVED 状态下提交核赔结论，记录核定赔付金额/给付方式/收款方， 触发理赔聚合流转至 PAID 并发布
 * {@code ClaimSettledEvent}（应用层据此触发支付域 CLAIM_PAYOUT）。
 * </p>
 */
public record SettleClaimCommand(
        @TargetAggregateIdentifier ClaimId claimId,
        BigDecimal settledAmount,
        ClaimEnum.PayoutMethod payoutMethod,
        String payeeAccount,
        String conclusion
) {
}
