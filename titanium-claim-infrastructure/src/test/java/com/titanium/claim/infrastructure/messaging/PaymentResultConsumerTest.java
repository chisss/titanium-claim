package com.titanium.claim.infrastructure.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.titanium.claim.application.orchestration.payment.PaymentCompletionOrchestrator;
import com.titanium.common.context.RequestContextHolder;
import com.titanium.common.messaging.KafkaIdempotencyGuard;

/**
 * 支付结果入站消费者用例
 * <p>
 * 锁死三件事：① 本域支付消息（businessType=CLAIM）正确回写赔案；
 * ② 🔴 域外支付消息必须被跳过 —— {@code payment-order-paid} 是支付域对**所有**业务域的统一出口，
 * 保单域保费收取成功同样发布到本主题，若不过滤则保费消息的 businessId（保单ID）会被当作赔案ID
 * 回写，赔案不存在 → 抛异常重抛 → 消息被无限重放；
 * ③ 🔴 幂等语义（m22-07 / MSG-02）——去重、失败释放、身份粒度三件承重事。
 * </p>
 * <p>
 * 幂等用例接入的是<b>真实</b> {@link KafkaIdempotencyGuard}（仅把 Redis 换成键空间模拟），
 * 而非 mock 守卫：mock 只能证明「调了某个方法」，证明不了「重复投递真的被拦下」「失败后真的能重投」
 * 「重派真的不算重复」——而后者才是本改造的目的。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class PaymentResultConsumerTest {

    /** 模拟 Redis 键空间：SETNX 的 put-if-absent 与 DELETE 的 remove 语义由集合天然提供 */
    private final Set<String> redisKeys = ConcurrentHashMap.newKeySet();

    @Mock
    private PaymentCompletionOrchestrator paymentCompletionOrchestrator;

    private PaymentResultConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new PaymentResultConsumer(paymentCompletionOrchestrator, newGuardOverFakeRedis());
    }

    private KafkaIdempotencyGuard newGuardOverFakeRedis() {
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = Mockito.mock(ValueOperations.class);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenAnswer(invocation -> redisKeys.add(invocation.getArgument(0)));
        lenient().when(redisTemplate.delete(anyString()))
                .thenAnswer(invocation -> redisKeys.remove(invocation.getArgument(0)));
        return new KafkaIdempotencyGuard(redisTemplate);
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
        return payload(businessId, businessType, "PAY-NO-001");
    }

    /** 指定支付单号的载荷：同一赔案在出账失败后会被人工重派，重派生成**新** paymentNo（m21-13）。 */
    private String payload(String businessId, String businessType, String paymentNo) {
        return """
                {"paymentId":"PAY-001","thirdPartyTradeNo":"THIRD-001","status":"SUCCESS",
                 "paidAt":"2026-09-11T10:00:00","processedBy":"tester","tenantId":"TENANT-001",
                 "paymentNo":"%s","businessId":"%s","businessType":"%s",
                 "amount":8888.00,"currency":"CNY"}
                """.formatted(paymentNo, businessId, businessType);
    }

    /**
     * 支付域 {@code PaymentOrderFailedMessage} 经 fastjson2 序列化后的载荷形态。
     * {@code occurredAt} 按对端实际形态（空格分隔，非 ISO 的 {@code T} 分隔）书写——
     * 入站 record 若按 ISO 认知接收，此用例会直接反序列化失败。
     * <p>
     * 🔴 {@code tenantId} 为对端实际载荷（m6-901 补齐 D16 判据）且本域入站 record **已承接**——
     * 消费线程无请求上下文，跨域载荷是该线程判定租户归属的唯一依据。
     * </p>
     */
    private String failedPayload(String businessId, String businessType) {
        return """
                {"paymentNo":"PAY-NO-001","businessId":"%s","businessType":"%s","tenantId":"TENANT-001",
                 "resultType":"FAILED","reason":"渠道返回账户异常","occurredAt":"2026-09-11 10:00:00"}
                """.formatted(businessId, businessType);
    }

    /**
     * 回写在无请求上下文的 Kafka 监听线程中执行：身份必须在**派发命令之前**按载荷建立，
     * 否则下游 Axon 事件线程继承不到租户，出站 Feign（{@code /api/v1/**} 强制准入）必被 400 拦下；
     * 作用域退出后须还原，不污染线程池复用线程。
     */
    @Test
    void shouldSeedTenantScopeBeforeDelegatingPaidWriteBack() {
        String before = RequestContextHolder.getTenantId();
        AtomicReference<String> tenantDuringDelegation = new AtomicReference<>();
        doAnswer(invocation -> {
            tenantDuringDelegation.set(RequestContextHolder.getTenantId());
            return null;
        }).when(paymentCompletionOrchestrator).completePayment(anyString(), anyString());

        consumer.onPaymentPaid(payload("CLAIM-001", "CLAIM"));

        assertNull(before, "监听线程本无请求上下文");
        assertEquals("TENANT-001", tenantDuringDelegation.get(), "委托期间必须处于载荷租户作用域内");
        assertEquals(before, RequestContextHolder.getTenantId(), "作用域退出后须还原进入前的上下文");
    }

    /** 失败回写路径同源：同样必须先建立身份作用域再委托。 */
    @Test
    void shouldSeedTenantScopeBeforeDelegatingFailureWriteBack() {
        String before = RequestContextHolder.getTenantId();
        AtomicReference<String> tenantDuringDelegation = new AtomicReference<>();
        doAnswer(invocation -> {
            tenantDuringDelegation.set(RequestContextHolder.getTenantId());
            return null;
        }).when(paymentCompletionOrchestrator)
                .recordPaymentFailure(anyString(), anyString(), anyString(), anyString());

        consumer.onPaymentFailed(failedPayload("CLAIM-001", "CLAIM"));

        assertNull(before, "监听线程本无请求上下文");
        assertEquals("TENANT-001", tenantDuringDelegation.get(), "委托期间必须处于载荷租户作用域内");
        assertEquals(before, RequestContextHolder.getTenantId(), "作用域退出后须还原进入前的上下文");
    }

    /**
     * 载荷缺租户：数据缺陷（对端出站载荷自该字段引入起即携带），重投不可能成功 ⇒ 记 WARN 跳过、不重抛；
     * 且**不进编排**——不为出站留下无身份的执行路径。
     */
    @Test
    void shouldSkipPaidMessageWithoutTenant() {
        consumer.onPaymentPaid("""
                {"paymentNo":"PAY-NO-001","businessId":"CLAIM-001","businessType":"CLAIM","amount":8888.00}
                """);

        verifyNoInteractions(paymentCompletionOrchestrator);
    }

    /** 未成功消息同源：缺租户同样跳过，不委派。 */
    @Test
    void shouldSkipFailedMessageWithoutTenant() {
        consumer.onPaymentFailed("""
                {"paymentNo":"PAY-NO-001","businessId":"CLAIM-001","businessType":"CLAIM",
                 "resultType":"FAILED","reason":"渠道返回账户异常"}
                """);

        verifyNoInteractions(paymentCompletionOrchestrator);
    }

    /**
     * 🔴 幂等承重之一：同一消息被重复投递（at-least-once 的常态）时只回写一次。
     * <p>重复投递若穿透到聚合，会因 {@code status != APPROVED}（首次已置 PAID）抛异常，
     * 一路重试到 DLT —— 幂等守卫把这条「本该无副作用的重投」就地短路。</p>
     */
    @Test
    void shouldSkipDuplicateDeliveryOfSamePayment() {
        consumer.onPaymentPaid(payload("CLAIM-001", "CLAIM", "PAY-NO-001"));
        consumer.onPaymentPaid(payload("CLAIM-001", "CLAIM", "PAY-NO-001"));

        verify(paymentCompletionOrchestrator, times(1)).completePayment("CLAIM-001", "PAY-NO-001");
    }

    /**
     * 🔴 幂等承重之二（身份粒度）：同一赔案、**不同支付单**不算重复。
     * <p>赔付出账失败后人工重派会生成新 paymentNo（m21-13 的赔付可重派）——若幂等身份只用 claimId，
     * 重派成功的这次回写会被误判为重复而永久跳过，赔案卡在 APPROVED 且再无消息可等。
     * 故身份段必须含 paymentNo。</p>
     */
    @Test
    void shouldNotTreatRedispatchAsDuplicateWhenPaymentNoDiffers() {
        consumer.onPaymentPaid(payload("CLAIM-001", "CLAIM", "PAY-NO-001"));
        consumer.onPaymentPaid(payload("CLAIM-001", "CLAIM", "PAY-NO-001-R1"));

        verify(paymentCompletionOrchestrator).completePayment("CLAIM-001", "PAY-NO-001");
        verify(paymentCompletionOrchestrator).completePayment("CLAIM-001", "PAY-NO-001-R1");
    }

    /**
     * 🔴 幂等承重之三（失败释放）：回写失败后占位必须释放，否则重投递被当成重复投递而永久跳过——
     * 「可重试的失败」退化为「永久丢失」。本用例用同一载荷投递两次：首投失败、重投必须能再次回写。
     */
    @Test
    void shouldReleasePlaceholderOnFailureSoRedeliveryRetries() {
        doThrow(new IllegalStateException("赔案状态暂不允许回写")).doNothing().when(paymentCompletionOrchestrator)
                .completePayment(anyString(), anyString());

        assertThrows(IllegalStateException.class, () -> consumer.onPaymentPaid(payload("CLAIM-001", "CLAIM")));
        consumer.onPaymentPaid(payload("CLAIM-001", "CLAIM"));

        verify(paymentCompletionOrchestrator, times(2)).completePayment("CLAIM-001", "PAY-NO-001");
    }

    /**
     * 与上条成对：回写**成功**后不得释放占位，否则真正的重复投递会被当作首次消费而重复回写
     * （幂等守卫自我失效）。
     */
    @Test
    void shouldKeepPlaceholderAfterSuccess() {
        consumer.onPaymentPaid(payload("CLAIM-001", "CLAIM"));

        assertEquals(1, redisKeys.size(), "成功后必须保留占位，供后续重复投递比对");
    }
}
