package com.titanium.claim.event;

import java.time.LocalDateTime;

import com.titanium.claim.common.enums.PaymentFailureType;
import com.titanium.claim.valueobject.ClaimId;

/**
 * 赔付失败事件
 * <p>
 * 赔付支付回写：支付域出账未成功（渠道确认失败 / 人工取消）后，赔付状态流转至 {@code FAILED}，
 * 案件状态**保持 APPROVED** 待人工重派（赔付决定不因出款受阻而改变）。携带支付单号、失败类型与原因
 * 供对账与运营处置。
 * </p>
 *
 * @param failureType 失败类型；对端 code 无法识别时为 {@code null}（未知类型不阻断回写）
 */
public record ClaimPaymentFailedEvent(
        ClaimId claimId,
        String paymentNo,
        PaymentFailureType failureType,
        String failureReason,
        LocalDateTime failedAt
) {
}
