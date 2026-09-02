package com.titanium.claim.port.notification;

/**
 * 通知服务出口 Port（对端域：notification）
 * <p>
 * 领域需要的通知能力契约：拒赔通知书/补件通知等理赔通知触发。
 * 实现为 infrastructure 层 {@code adapter/notification/NotificationServiceAdapter}（经 Kafka
 * {@code claim-rejected} 主题发消息，notification 域防腐消费后渲染发送）。
 * </p>
 */
public interface NotificationServicePort {

    /**
     * 发送拒赔通知（拒赔通知书）。
     *
     * @param notice 拒赔通知内容
     */
    void sendRejectionNotice(RejectionNotice notice);

    /**
     * 拒赔通知内容（NotificationServicePort 入参，领域出站契约 record）
     *
     * @param claimId    理赔案件ID
     * @param policyId   关联保单ID
     * @param customerId 客户ID
     * @param reasonCode 拒赔原因编码（RejectReason code）
     * @param comment    拒赔说明
     */
    record RejectionNotice(
            String claimId,
            String policyId,
            String customerId,
            String reasonCode,
            String comment
    ) {
    }
}
