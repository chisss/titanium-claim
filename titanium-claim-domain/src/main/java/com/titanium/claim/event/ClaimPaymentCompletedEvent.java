package com.titanium.claim.event;

import java.time.LocalDateTime;

import com.titanium.claim.valueobject.ClaimId;

/**
 * 赔款支付完成事件
 * <p>
 * 赔付支付 Saga 回写：支付域出账成功后流转赔案至 PAID，携带支付单号供对账。
 * </p>
 */
public record ClaimPaymentCompletedEvent(
        ClaimId claimId,
        String paymentNo,
        LocalDateTime paidAt
) {
}
