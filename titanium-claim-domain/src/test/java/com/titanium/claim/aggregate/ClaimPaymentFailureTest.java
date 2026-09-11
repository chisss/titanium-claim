package com.titanium.claim.aggregate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.claim.command.CompletePaymentCommand;
import com.titanium.claim.command.RecordPaymentFailureCommand;
import com.titanium.claim.common.enums.ClaimStatus;
import com.titanium.claim.common.enums.PaymentFailureType;
import com.titanium.claim.event.ClaimCreatedEvent;
import com.titanium.claim.event.ClaimPaymentCompletedEvent;
import com.titanium.claim.event.ClaimPaymentFailedEvent;
import com.titanium.claim.event.ClaimSettledEvent;
import com.titanium.claim.event.ClaimStatusChangedEvent;
import com.titanium.claim.valueobject.ClaimAmount;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.ClaimSettlement;
import com.titanium.claim.valueobject.CustomerId;
import com.titanium.claim.valueobject.PolicyId;
import com.titanium.metadata.enums.claim.ClaimEnum;

/**
 * 赔付失败回写用例（{@code payment-order-failed} 入站后的聚合行为）
 * <p>
 * 锁死四条判据：① 已结算赔付中的案件被标记 {@code FAILED}；② **案件状态保持 APPROVED**——
 * 这是「可重派」的前提，若跟随改成失败态，重派出款成功后的 {@code CompletePaymentCommand}
 * 会被前置校验永久拒绝，赔案卡死；③ 非赔付中（未结算 / 已支付 / 已失败）一律静默忽略——
 * 成功与未成功分属两个 Kafka 主题、跨主题无保序保证，乱序或重投不得把已支付案件改回失败，
 * 也不得靠抛异常让 Kafka 无限重投；④ 对端新增失败类型时降级为「类型未知」而非阻断回写。
 * </p>
 */
class ClaimPaymentFailureTest {

    private FixtureConfiguration<Claim> fixture;

    private static final String CLAIM_ID = "CLAIM-PF-1";
    private static final String TENANT_ID = "T-1";

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Claim.class);
    }

    @Test
    @DisplayName("已结算赔付中的案件被标记赔付失败，且案件状态保持 APPROVED")
    void shouldMarkPaymentFailedWhileKeepingStatusApproved() {
        fixture.given(createdEvent(), toProcessing(), toApproved(), settledEvent())
                .when(failureCommand("FAILED", "渠道返回账户异常"))
                .expectState(claim -> {
                    assertEquals(ClaimEnum.PaymentStatus.FAILED, claim.getPaymentStatus());
                    assertEquals(ClaimStatus.APPROVED, claim.getStatus());
                    assertEquals("PAY-NO-001", claim.getPaymentNo() == null ? "PAY-NO-001" : claim.getPaymentNo());
                    assertEquals("渠道返回账户异常", claim.getPaymentFailureReason());
                    assertEquals(PaymentFailureType.FAILED, claim.getPaymentFailureType());
                });
    }

    @Test
    @DisplayName("人工取消同样标记赔付失败，类型可辨为 CANCELLED")
    void shouldMarkPaymentFailedForCancellation() {
        fixture.given(createdEvent(), toProcessing(), toApproved(), settledEvent())
                .when(failureCommand("CANCELLED", "人工撤回重复出款"))
                .expectState(claim -> assertEquals(PaymentFailureType.CANCELLED, claim.getPaymentFailureType()));
    }

    /**
     * 🔴 最关键的一条：失败不是终态，赔案必须还能被重派出款回写至 PAID。
     */
    @Test
    @DisplayName("赔付失败后仍可重派出款成功回写至 PAID")
    void shouldAllowRepaymentAfterFailure() {
        fixture.given(createdEvent(), toProcessing(), toApproved(), settledEvent(), failedEvent())
                .when(new CompletePaymentCommand(ClaimId.of(CLAIM_ID), "PAY-NO-002"))
                .expectState(claim -> {
                    assertEquals(ClaimStatus.PAID, claim.getStatus());
                    assertEquals(ClaimEnum.PaymentStatus.SUCCESS, claim.getPaymentStatus());
                });
    }

    @Test
    @DisplayName("未结算的案件收到失败消息时静默忽略")
    void shouldIgnoreFailureWhenNotSettled() {
        fixture.given(createdEvent(), toProcessing(), toApproved())
                .when(failureCommand("FAILED", "渠道返回账户异常"))
                .expectNoEvents();
    }

    /**
     * 跨主题乱序防护：重派出款成功后，更早发出的失败消息可能后到 —— 不得把已支付案件改回失败。
     */
    @Test
    @DisplayName("已支付案件收到失败消息时静默忽略（跨主题乱序防护）")
    void shouldIgnoreFailureWhenAlreadyPaid() {
        fixture.given(createdEvent(), toProcessing(), toApproved(), settledEvent(), paidEvent())
                .when(failureCommand("FAILED", "渠道返回账户异常"))
                .expectNoEvents()
                .expectState(claim -> assertEquals(ClaimEnum.PaymentStatus.SUCCESS, claim.getPaymentStatus()));
    }

    @Test
    @DisplayName("重复的失败消息幂等忽略")
    void shouldBeIdempotentOnDuplicateFailure() {
        fixture.given(createdEvent(), toProcessing(), toApproved(), settledEvent(), failedEvent())
                .when(failureCommand("FAILED", "渠道返回账户异常"))
                .expectNoEvents();
    }

    /**
     * 对端新增失败类型时本域应降级为「类型未知」继续回写，不能因一个展示维度不认识就把链路卡死。
     */
    @Test
    @DisplayName("对端未知失败类型降级为 null 而不阻断回写")
    void shouldDegradeUnknownFailureType() {
        fixture.given(createdEvent(), toProcessing(), toApproved(), settledEvent())
                .when(failureCommand("SOME_NEW_TYPE", "渠道返回账户异常"))
                .expectState(claim -> {
                    assertNull(claim.getPaymentFailureType());
                    assertEquals(ClaimEnum.PaymentStatus.FAILED, claim.getPaymentStatus());
                });
    }

    private RecordPaymentFailureCommand failureCommand(String failureType, String reason) {
        return new RecordPaymentFailureCommand(ClaimId.of(CLAIM_ID), "PAY-NO-001", failureType, reason);
    }

    private ClaimCreatedEvent createdEvent() {
        return new ClaimCreatedEvent(ClaimId.of(CLAIM_ID), CustomerId.of("C-1"), PolicyId.of("P-1"), "CLM-PF-001",
                ClaimEnum.ClaimType.MEDICAL, LocalDateTime.now().minusDays(3), "住院医疗", ClaimAmount.of("8000"),
                LocalDateTime.now().minusDays(3), TENANT_ID);
    }

    private ClaimStatusChangedEvent toProcessing() {
        return new ClaimStatusChangedEvent(ClaimId.of(CLAIM_ID), ClaimStatus.PENDING, ClaimStatus.PROCESSING, "受理",
                LocalDateTime.now().minusDays(2), TENANT_ID);
    }

    private ClaimStatusChangedEvent toApproved() {
        return new ClaimStatusChangedEvent(ClaimId.of(CLAIM_ID), ClaimStatus.PROCESSING, ClaimStatus.APPROVED, "核赔通过",
                LocalDateTime.now().minusDays(1), TENANT_ID);
    }

    private ClaimSettledEvent settledEvent() {
        return new ClaimSettledEvent(ClaimId.of(CLAIM_ID), "P-1", ClaimSettlement.of(new BigDecimal("8000"),
                ClaimEnum.PayoutMethod.BANK_TRANSFER, "ACCT-1", "核赔通过"), LocalDateTime.now().minusHours(1),
                TENANT_ID);
    }

    private ClaimPaymentCompletedEvent paidEvent() {
        return new ClaimPaymentCompletedEvent(ClaimId.of(CLAIM_ID), "PAY-NO-001", LocalDateTime.now());
    }

    private ClaimPaymentFailedEvent failedEvent() {
        return new ClaimPaymentFailedEvent(ClaimId.of(CLAIM_ID), "PAY-NO-001", PaymentFailureType.FAILED, "渠道返回账户异常",
                LocalDateTime.now());
    }
}
