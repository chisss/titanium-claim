package com.titanium.claim.event;

import java.time.LocalDateTime;

import com.titanium.claim.valueobject.ClaimId;
import com.titanium.metadata.enums.claim.RejectReason;

/**
 * 理赔拒赔事件
 * <p>
 * 核赔否决后发布：携枚举化拒赔原因与客户/保单标识，投影流转读模型至 REJECTED，
 * 并经 Kafka 触发拒赔通知书发送（3 日时限）。
 * </p>
 * <p>
 * 🔴 <b>跨域契约</b>（2026-09-14 m6-905）：本事件经 {@code claim-rejected} 主题外发，唯一消费方是
 * notification 域 {@code ClaimRejectedEventListener}，以其序列化 JSON 为载荷（无独立通知契约类）。
 * 消费方据此渲染通知书所需字段：{@code customerId}（收件人）、{@code reason}（拒赔原因码，渲染中文名）、
 * {@code rejectedAt}（拒赔时间）、{@code tenantId}。载荷<b>不含客户手机号/邮箱与保单号</b>——
 * 前者本域无从取得（需另建客户域查询能力），故通知走站内信投递（详见 notification 侧装配器说明）；
 * 后者为「不为下游通知反向修改上游事件契约」的既定纪律（同 {@code PolicyIssuedEvent} 先例）。
 * 字段增删须同步核对 notification 的入站防腐镜像 {@code ClaimRejectedMessage}。
 * </p>
 */
public record ClaimRejectedEvent(
        ClaimId claimId,
        String policyId,
        String customerId,
        RejectReason reason,
        String comment,
        LocalDateTime rejectedAt,
        String tenantId
) {
}
