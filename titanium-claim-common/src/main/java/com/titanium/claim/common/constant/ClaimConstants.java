package com.titanium.claim.common.constant;

public class ClaimConstants {

    private ClaimConstants() {
        // 私有构造方法，防止实例化
    }

    // 理赔状态常量
    public static final String CLAIM_STATUS_PENDING = "PENDING";
    public static final String CLAIM_STATUS_PROCESSING = "PROCESSING";
    public static final String CLAIM_STATUS_APPROVED = "APPROVED";
    public static final String CLAIM_STATUS_REJECTED = "REJECTED";
    public static final String CLAIM_STATUS_PAID = "PAID";
    public static final String CLAIM_STATUS_CLOSED = "CLOSED";

    // Kafka主题常量
    public static class KafkaTopic {
        public static final String CLAIM_CREATED = "claim-created";
        public static final String CLAIM_UPDATED = "claim-updated";
        public static final String CLAIM_STATUS_CHANGED = "claim-status-changed";
        public static final String POLICY_VALIDATED = "policy-validated";
        public static final String PAYMENT_PROCESSED = "payment-processed";
        /** 身故给付结算主题：供 policy 域防腐监听器消费以终止保单（给付后保单责任终结） */
        public static final String DEATH_BENEFIT_SETTLED = "claim-death-benefit-settled";
        /** 理赔拒赔主题：供 notification 域/下游消费触发拒赔通知书发送 */
        public static final String CLAIM_REJECTED = "claim-rejected";
        /** 理赔赔付支付单主题：供 payment 域防腐消费创建 CLAIM_PAYOUT 支付单 */
        public static final String PAYMENT_ORDER_CREATED = "payment-order-created";
    }
}
