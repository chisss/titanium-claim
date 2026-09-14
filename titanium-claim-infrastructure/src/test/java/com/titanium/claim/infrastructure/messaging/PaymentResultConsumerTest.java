package com.titanium.claim.infrastructure.messaging;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.titanium.claim.application.orchestration.payment.PaymentCompletionOrchestrator;

/**
 * 支付结果入站消费者用例
 * <p>
 * 锁死两件事：① 本域支付消息（businessType=CLAIM）正确回写赔案；
 * ② 🔴 域外支付消息必须被跳过 —— {@code payment-order-paid} 是支付域对**所有**业务域的统一出口，
 * 保单域保费收取成功同样发布到本主题，若不过滤则保费消息的 businessId（保单ID）会被当作赔案ID
 * 回写，赔案不存在 → 抛异常重抛 → 消息被无限重放。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class PaymentResultConsumerTest {

    @Mock
    private PaymentCompletionOrchestrator paymentCompletionOrchestrator;

    private PaymentResultConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new PaymentResultConsumer(paymentCompletionOrchestrator);
    }

    @Test
    void shouldWriteBackClaimWhenBusinessTypeIsClaim() {
        consumer.onPaymentPaid(payload("CLAIM-001", "CLAIM"));

        verify(paymentCompletionOrchestrator).completePayment("CLAIM-001", "PAY-NO-001");
    }

    /**
     * 保单域保费收取也会走本主题，businessId 是保单ID —— 误当作赔案回写会让消息无限重放。
     */
    @Test
    void shouldSkipMessageFromOtherBusinessDomain() {
        consumer.onPaymentPaid(payload("POLICY-001", "POLICY"));

        verifyNoInteractions(paymentCompletionOrchestrator);
    }

    @Test
    void shouldSkipMessageMissingRequiredFields() {
        consumer.onPaymentPaid("{\"businessType\":\"CLAIM\",\"amount\":8888.00}");

        verify(paymentCompletionOrchestrator, never()).completePayment(anyString(), anyString());
    }

    /**
     * 回写失败必须重抛，交 Kafka 重试/DLQ 兜底 —— 与出款指令入站监听器的「吞异常」策略有意不同：
     * 此处重试可能成功（赔案状态可被其它流程推进），重试不可能成功的场景才该吞。
     */
    @Test
    void shouldRethrowWhenWriteBackFails() {
        doThrow(new IllegalStateException("赔案状态不允许回写"))
                .when(paymentCompletionOrchestrator).completePayment(anyString(), anyString());

        assertThrows(IllegalStateException.class, () -> consumer.onPaymentPaid(payload("CLAIM-002", "CLAIM")));
    }

    /**
     * 出账未成功消息同样须按 businessType 过滤：本主题是支付域对**所有**业务域出款未成功的统一出口，
     * 不过滤则保费/退费业务的 businessId（保单ID/保全单ID）会被当作赔案ID 回写。
     */
    @Test
    void shouldRecordPaymentFailureWhenBusinessTypeIsClaim() {
        consumer.onPaymentFailed(failedPayload("CLAIM-001", "CLAIM"));

        verify(paymentCompletionOrchestrator).recordPaymentFailure("CLAIM-001", "PAY-NO-001", "FAILED",
                "渠道返回账户异常");
    }

    @Test
    void shouldSkipFailedMessageFromOtherBusinessDomain() {
        consumer.onPaymentFailed(failedPayload("POLICY-001", "POLICY"));

        verifyNoInteractions(paymentCompletionOrchestrator);
    }

    @Test
    void shouldSkipFailedMessageMissingRequiredFields() {
        consumer.onPaymentFailed("{\"businessType\":\"CLAIM\",\"resultType\":\"FAILED\"}");

        verify(paymentCompletionOrchestrator, never()).recordPaymentFailure(anyString(), anyString(), anyString(),
                anyString());
    }

    /**
     * 失败回写异常必须重抛（同成功路径）：聚合层已对「重复消息 / 跨主题乱序」做幂等静默，
     * 能抛到这里的只应是基础设施故障，重试是合理语义。
     */
    @Test
    void shouldRethrowWhenFailureWriteBackFails() {
        doThrow(new IllegalStateException("数据库不可达")).when(paymentCompletionOrchestrator)
                .recordPaymentFailure(anyString(), anyString(), anyString(), anyString());

        assertThrows(IllegalStateException.class, () -> consumer.onPaymentFailed(failedPayload("CLAIM-002", "CLAIM")));
    }

    /**
     * 支付域 {@code PaymentProcessedEvent} 经 fastjson2 序列化后的载荷形态（字段名与对端一致）。
     */
    private String payload(String businessId, String businessType) {
        return """
                {"paymentId":"PAY-001","thirdPartyTradeNo":"THIRD-001","status":"SUCCESS",
                 "paidAt":"2026-09-11T10:00:00","processedBy":"tester","tenantId":"TENANT-001",
                 "paymentNo":"PAY-NO-001","businessId":"%s","businessType":"%s",
                 "amount":8888.00,"currency":"CNY"}
                """.formatted(businessId, businessType);
    }

    /**
     * 支付域 {@code PaymentOrderFailedMessage} 经 fastjson2 序列化后的载荷形态。
     * {@code occurredAt} 按对端实际形态（空格分隔，非 ISO 的 {@code T} 分隔）书写——
     * 入站 record 若按 ISO 认知接收，此用例会直接反序列化失败。
     * <p>
     * 🔴 {@code tenantId} 按对端新增后的实际载荷书写（m6-901 补齐 D16 判据）：本域入站 record
     * **刻意不镜像该字段**（只承接本域用到的字段，与成功路径同做法），故本用例同时证明
     * 「对端新增字段不会让本域反序列化失败」——新增可空字段的向后兼容由此锁死。
     * </p>
     */
    private String failedPayload(String businessId, String businessType) {
        return """
                {"paymentNo":"PAY-NO-001","businessId":"%s","businessType":"%s","tenantId":"TENANT-001",
                 "resultType":"FAILED","reason":"渠道返回账户异常","occurredAt":"2026-09-11 10:00:00"}
                """.formatted(businessId, businessType);
    }
}
