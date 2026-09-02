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

    // 反欺诈警示规则常量（自动打标判据：命中规则标识 ruleCode，落库供审计追溯「哪个规则打的标」）
    public static class AlertRule {
        /** 延迟报案判据：报案时间距出险时间超过 30 天 */
        public static final long   LATE_REPORT_WINDOW_DAYS = 30L;
        /** 多次报案判据：同保单 30 天内存在其他报案 */
        public static final long   MULTIPLE_REPORTS_WINDOW_DAYS = 30L;
        /** 规则标识 key：报案时间距出险时间超过 30 天（ClaimAlertOrchestrator 自动打标） */
        public static final String RULE_LATE_REPORT = "ALERT_RULE_LATE_REPORT_30D";
        /** 规则标识 key：同保单 30 天内存在其他报案（ClaimAlertOrchestrator 自动打标） */
        public static final String RULE_MULTIPLE_REPORTS = "ALERT_RULE_MULTIPLE_REPORTS_30D";
        /** 规则标识 key：快赔通道自动核赔（QuickPayOrchestrator 打统计口径标记） */
        public static final String RULE_QUICK_PAY = "ALERT_RULE_QUICK_PAY_AUTO";
    }

    // 快赔通道常量（产品文档 §2.10：小额快赔自动理算核赔）
    /** 快赔核赔意见（落库文案常量化，红线 20：进入 ClaimSettlement conclusion 落库，禁裸中文字符串） */
    public static final String QUICK_PAY_CONCLUSION = "小额快赔自动核赔";

    // Kafka主题常量
    public static class KafkaTopic {
        public static final String CLAIM_CREATED = "claim-created";
        public static final String CLAIM_UPDATED = "claim-updated";
        public static final String CLAIM_STATUS_CHANGED = "claim-status-changed";
        public static final String POLICY_VALIDATED = "policy-validated";
        public static final String PAYMENT_PROCESSED = "payment-processed";
        /** 身故给付结算主题：供 policy 域防腐监听器消费以终止保单（给付后保单责任终结） */
        public static final String DEATH_BENEFIT_SETTLED = "claim-death-benefit-settled";
        /** 全残给付结算主题：供 policy 域防腐监听器消费以终止保单（给付后保单责任终结，同身故） */
        public static final String DISABILITY_BENEFIT_SETTLED = "claim-disability-benefit-settled";
        /** 理赔拒赔主题：供 notification 域/下游消费触发拒赔通知书发送 */
        public static final String CLAIM_REJECTED = "claim-rejected";
        /** 理赔赔付支付单主题：供 payment 域防腐消费创建 CLAIM_PAYOUT 支付单 */
        public static final String PAYMENT_ORDER_CREATED = "payment-order-created";
        /** 支付出账成功主题：payment 域发布，claim 域防腐消费回写 CompletePaymentCommand */
        public static final String PAYMENT_ORDER_PAID = "payment-order-paid";
    }
}
