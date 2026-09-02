package com.titanium.claim.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.claim.valueobject.ClaimId;

/**
 * 支付完成命令
 * <p>
 * 赔付支付 Saga 在支付域出账成功后回写赔案：仅 APPROVED 且已结算（待支付）状态可支付完成，
 * 流转至 PAID 终态并发布 {@code ClaimPaymentCompletedEvent}。
 * </p>
 *
 * @param claimId 理赔案件ID
 * @param paymentNo 支付单号（payment 域流水号，用于对账）
 */
public record CompletePaymentCommand(
        @TargetAggregateIdentifier ClaimId claimId,
        String paymentNo
) {
}
