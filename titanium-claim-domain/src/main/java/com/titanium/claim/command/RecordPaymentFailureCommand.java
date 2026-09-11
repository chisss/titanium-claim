package com.titanium.claim.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.claim.valueobject.ClaimId;

/**
 * 赔付失败回写命令
 * <p>
 * 支付域出账未成功（渠道确认失败 / 人工取消）后回写赔案：标记赔付状态为 {@code FAILED} 并记录失败类型
 * 与原因，**案件状态保持 APPROVED** —— 赔付决定不因出款受阻而改变，赔案仍待人工重派出款；
 * 重派成功后 {@link CompletePaymentCommand} 照常回写至 PAID。
 * </p>
 *
 * @param claimId 理赔案件ID
 * @param paymentNo 支付单号（payment 域流水号，用于对账）
 * @param failureType 失败类型（对端 {@code resultType} code，见
 *        {@link com.titanium.claim.common.enums.PaymentFailureType}）
 * @param failureReason 出款未成功原因（渠道返回的失败原因或人工取消原因）
 */
public record RecordPaymentFailureCommand(
        @TargetAggregateIdentifier ClaimId claimId,
        String paymentNo,
        String failureType,
        String failureReason
) {
}
