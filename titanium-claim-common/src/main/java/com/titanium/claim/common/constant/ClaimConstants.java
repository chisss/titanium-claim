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

    // 错误消息常量
    public static final String CLAIM_NOT_FOUND = "理赔记录不存在";
    public static final String INVALID_CLAIM_STATUS = "无效的理赔状态";
    public static final String INVALID_CLAIM_AMOUNT = "无效的理赔金额";
    public static final String CLAIM_ALREADY_PROCESSED = "理赔记录已处理";
    public static final String CUSTOMER_NOT_FOUND = "客户不存在";
    public static final String POLICY_NOT_FOUND = "保单不存在";
    public static final String POLICY_NOT_ACTIVE = "保单未生效";
    public static final String CLAIM_OUT_OF_COVERAGE = "理赔不在保险范围内";

    // Kafka主题常量
    public static class KafkaTopic {
        public static final String CLAIM_CREATED = "claim-created";
        public static final String CLAIM_UPDATED = "claim-updated";
        public static final String CLAIM_STATUS_CHANGED = "claim-status-changed";
        public static final String POLICY_VALIDATED = "policy-validated";
        public static final String PAYMENT_PROCESSED = "payment-processed";
        /** 身故给付结算主题：供 policy 域防腐监听器消费以终止保单（给付后保单责任终结） */
        public static final String DEATH_BENEFIT_SETTLED = "claim-death-benefit-settled";
    }
}
