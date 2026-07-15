package com.titanium.claim.event;

import java.time.LocalDateTime;

import com.titanium.claim.valueobject.BenefitCalculation;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.ClaimSettlement;
import com.titanium.claim.valueobject.DeathClaimEvidence;

/**
 * 身故给付结算事件（寿险身故理赔专属）
 * <p>
 * 身故理赔（{@code ClaimType.DEATH}）核赔通过并完成给付核定时发布，携带身故证据、受益人给付核算、
 * 核赔结论及关联保单。区别于通用赔付事件 {@link ClaimSettledEvent}：身故给付一次性给付后
 * <b>触发保单终止</b>（被保险人身故，保单责任终结），由 policy 域防腐监听器据 policyId 派发终止命令。
 * </p>
 *
 * @param claimId 理赔案件ID
 * @param policyId 关联保单ID（供跨域保单终止闭环）
 * @param evidence 身故证据材料集
 * @param benefitCalculation 身故给付金核算（按受益人份额分配）
 * @param settlement 核赔结论（给付总额/给付方式/收款方）
 * @param settledAt 结算时间
 */
public record DeathBenefitSettledEvent(
        ClaimId claimId,
        String policyId,
        DeathClaimEvidence evidence,
        BenefitCalculation benefitCalculation,
        ClaimSettlement settlement,
        LocalDateTime settledAt
) {
}
