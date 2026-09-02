package com.titanium.claim.infrastructure.messaging.inbound;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付出账成功防腐入站消息（对端域：payment）
 * <p>
 * payment 域 {@code payment-order-paid} 主题 JSON 载荷镜像，不依赖对端域类型。
 * 由 {@link com.titanium.claim.infrastructure.messaging.PaymentResultConsumer} 一次反序列化后
 * 派发 {@code CompletePaymentCommand} 回写赔案至 PAID。
 * </p>
 */
public record PaymentOrderPaidMessage(
        /** 支付单号 */
        String paymentNo,
        /** 业务单号（理赔案件ID，出站时以 claimId 为消息 key 与 businessId） */
        String businessId,
        /** 支付金额 */
        BigDecimal amount,
        /** 出账成功时间 */
        LocalDateTime paidAt
) {
}
