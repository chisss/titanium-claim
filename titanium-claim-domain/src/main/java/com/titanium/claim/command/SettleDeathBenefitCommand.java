package com.titanium.claim.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.claim.valueobject.BenefitCalculation;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.DeathClaimEvidence;
import com.titanium.metadata.enums.claim.ClaimEnum;

/**
 * 身故给付结算命令（寿险身故理赔专属）
 * <p>
 * 在理赔 APPROVED 状态下提交身故给付结论，携带身故证据（死亡证明/受益人关系）与受益人份额核算，
 * 触发理赔聚合流转至 PAID 并发布 {@code DeathBenefitSettledEvent}。区别于通用核赔结算
 * {@link SettleClaimCommand}：身故给付以被保险人身故为条件、按受益人份额一次性给付，
 * <b>给付后保单责任终止</b>（下游据事件派发保单终止），是定期寿险/终身寿险的核心给付场景。
 * </p>
 *
 * @param claimId 理赔案件ID
 * @param evidence 身故证据材料（死亡证明/身故日期/受益人关系证明）
 * @param benefitCalculation 身故给付金核算（给付总额按受益人份额分配）
 * @param payoutMethod 给付方式（BANK_TRANSFER/CASH 等）
 * @param conclusion 核赔意见
 */
public record SettleDeathBenefitCommand(
        @TargetAggregateIdentifier ClaimId claimId,
        DeathClaimEvidence evidence,
        BenefitCalculation benefitCalculation,
        ClaimEnum.PayoutMethod payoutMethod,
        String conclusion
) {
}
