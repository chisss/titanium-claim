package com.titanium.claim.event;

import java.time.LocalDateTime;

import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.ClaimSettlement;

/**
 * 理赔核赔结算事件
 * <p>
 * 核赔通过并完成赔付核定时发布，携带核赔结论（赔付金额/给付方式/收款方）。 应用层监听此事件触发支付域
 * CreatePaymentOrderCommand(paymentType=CLAIM_PAYOUT)。
 * </p>
 * <p>
 * 用于车险/医疗等「即付即了、不终止保单」的通用赔付；身故给付走独立的
 * {@code DeathBenefitSettledEvent}（携带身故证据/受益人核算并触发保单终止）。
 * </p>
 */
public record ClaimSettledEvent(
        ClaimId claimId,
        ClaimSettlement settlement,
        LocalDateTime settledAt
) {
}
