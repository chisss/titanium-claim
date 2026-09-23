package com.titanium.claim.infrastructure.messaging.inbound;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付出账成功防腐入站消息（对端域：payment）
 * <p>
 * payment 域 {@code payment-order-paid} 主题 JSON 载荷镜像（对端载荷为该域 {@code PaymentProcessedEvent}，
 * 本 record 只承接本域用到的字段），不依赖对端域类型。
 * 由 {@link com.titanium.claim.infrastructure.messaging.PaymentResultConsumer} 一次反序列化后
 * 派发 {@code CompletePaymentCommand} 回写赔案至 PAID。
 * </p>
 * <p>
 * 🔴 {@code tenantId} 必须承接：本域消费侧运行在 Kafka 监听线程，无 Web 请求上下文，
 * <b>跨域载荷是该线程判定事件归属租户的唯一依据</b>；消费端据以建立身份作用域，使出站 Feign
 * （{@code /api/v1/**} 强制准入）不因缺租户头被拦下。对端自该字段引入起即随载荷下发。
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
        LocalDateTime paidAt,
        /** 关联业务域：消费方据此过滤本域消息 */
        String businessType,
        /** 租户ID：消费侧无请求上下文时的身份来源 */
        String tenantId
) {
}
