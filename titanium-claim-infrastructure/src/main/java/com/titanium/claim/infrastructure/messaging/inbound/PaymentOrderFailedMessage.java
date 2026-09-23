package com.titanium.claim.infrastructure.messaging.inbound;

import java.time.LocalDateTime;

/**
 * 支付出账未成功入站消息（对端域：payment，{@code payment-order-failed} 主题）
 * <p>
 * 对端 JSON 的镜像 record，**不依赖对端域类型**，一次反序列化承接，避免逐字段手工拆包。
 * 字段名与 payment 域出站载荷一致；枚举型字段以 code 字符串承接（{@code businessType}="CLAIM"、
 * {@code resultType}="FAILED"/"CANCELLED"），跨域转换在本域消费端完成。
 * </p>
 * <p>
 * 只镜像本域用到的字段（与 {@link PaymentOrderPaidMessage} 同做法），对端载荷的其余审计字段不透传。
 * </p>
 * <p>
 * 🔴 {@code tenantId} 由「刻意不承接」改为<b>必须承接</b>（2026-09-22）：原判据是「本域按
 * {@code businessId}（claimId）定位赔案，聚合自身的事件流已带租户，回写链路无需该字段」——
 * 该判据只覆盖了**赔案定位**，漏了**出站身份**：本域消费侧运行在 Kafka 监听线程、无请求上下文，
 * 而回写链路派发的命令及其下游 Axon 事件线程要走出站 Feign（{@code /api/v1/**} 缺租户头即 400），
 * 跨域载荷是该线程判定租户归属的唯一依据。对端自 m6-901 起已随载荷下发该字段（D16 判据），
 * 故镜像侧补齐即可，无需回查本域读模型。
 * </p>
 */
public record PaymentOrderFailedMessage(
        /** 支付单号：对账依据 */
        String paymentNo,
        /** 关联业务单号：本域取之为理赔案件ID */
        String businessId,
        /** 关联业务域：据以过滤本域消息 */
        String businessType,
        /** 租户ID：消费侧无请求上下文时的身份来源 */
        String tenantId,
        /** 未成功结果类型：FAILED（渠道确认失败）/ CANCELLED（人工取消） */
        String resultType,
        /** 未成功原因：渠道失败原因或人工取消原因 */
        String reason,
        /** 结果发生时间（对端时间，本域仅作参考，聚合以自身时钟记录） */
        LocalDateTime occurredAt
) {
}
