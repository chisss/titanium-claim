package com.titanium.claim.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.claim.valueobject.BenefitCalculation;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.DisabilityClaimEvidence;
import com.titanium.metadata.enums.claim.ClaimEnum;

/**
 * 全残给付结算命令（寿险/意外险全残理赔专属，CLAIM-6）
 * <p>
 * 在理赔 APPROVED 状态下提交全残给付结论，携带残疾鉴定证据与受益人份额核算，
 * 触发理赔聚合流转至赔付中并发布 {@code DisabilityBenefitSettledEvent}。与身故给付
 * {@link SettleDeathBenefitCommand} 并列：全残给付以被保险人全残为条件、按受益人份额一次性给付，
 * <b>给付后保单责任终止</b>（下游据事件派发保单终止，同身故）。
 * </p>
 *
 * @param claimId            理赔案件ID
 * @param evidence           全残证据材料（残疾鉴定证明/残疾等级/受益人关系证明）
 * @param benefitCalculation 全残给付金核算（给付总额按受益人份额分配）
 * @param payoutMethod       给付方式（BANK_TRANSFER/CASH 等）
 * @param conclusion         核赔意见
 */
public record SettleDisabilityBenefitCommand(
        @TargetAggregateIdentifier ClaimId claimId,
        DisabilityClaimEvidence evidence,
        BenefitCalculation benefitCalculation,
        ClaimEnum.PayoutMethod payoutMethod,
        String conclusion
) {
}
