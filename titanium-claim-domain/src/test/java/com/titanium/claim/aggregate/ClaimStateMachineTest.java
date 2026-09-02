package com.titanium.claim.aggregate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.claim.command.ChangeClaimStatusCommand;
import com.titanium.claim.command.CloseClaimCommand;
import com.titanium.claim.command.RejectClaimCommand;
import com.titanium.claim.common.enums.ClaimStatus;
import com.titanium.claim.common.enums.RejectReason;
import com.titanium.claim.event.ClaimCreatedEvent;
import com.titanium.claim.event.ClaimRejectedEvent;
import com.titanium.claim.event.ClaimSettledEvent;
import com.titanium.claim.event.ClaimStatusChangedEvent;
import com.titanium.claim.exception.ClaimStatusPreconditionException;
import com.titanium.claim.exception.ClaimStatusTransitionException;
import com.titanium.claim.valueobject.ClaimAmount;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.ClaimSettlement;
import com.titanium.claim.valueobject.CustomerId;
import com.titanium.claim.valueobject.PolicyId;
import com.titanium.metadata.enums.claim.ClaimEnum;

/**
 * 理赔状态机测试（拒赔/赔付回写/结案闭环）
 * <p>
 * 验证 {@code RejectClaimCommand}（PENDING/PROCESSING→REJECTED）、{@code CompletePaymentCommand}
 * （APPROVED+已结算→PAID）、{@code CloseClaimCommand}（PAID/REJECTED→CLOSED）及通用状态变更通道的
 * 非法流转拦截。事件含 {@code now()}，用 expectSuccessfulHandlerExecution 断言执行成功，不做精确时间比对。
 * </p>
 */
class ClaimStateMachineTest {

    private FixtureConfiguration<Claim> fixture;

    private static final String CLAIM_ID = "CLAIM-SM-1";

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Claim.class);
    }

    private ClaimCreatedEvent createdEvent() {
        return new ClaimCreatedEvent(ClaimId.of(CLAIM_ID), CustomerId.of("C-1"), PolicyId.of("P-1"), "CLM-SM-001",
                ClaimEnum.ClaimType.MEDICAL, LocalDateTime.now().minusDays(3), "住院医疗", ClaimAmount.of("8000"),
                LocalDateTime.now().minusDays(3));
    }

    private ClaimStatusChangedEvent toProcessing() {
        return new ClaimStatusChangedEvent(ClaimId.of(CLAIM_ID), ClaimStatus.PENDING, ClaimStatus.PROCESSING, "受理",
                LocalDateTime.now().minusDays(2));
    }

    private ClaimStatusChangedEvent toApproved() {
        return new ClaimStatusChangedEvent(ClaimId.of(CLAIM_ID), ClaimStatus.PROCESSING, ClaimStatus.APPROVED, "核赔通过",
                LocalDateTime.now().minusDays(1));
    }

    private ClaimSettledEvent settledEvent() {
        return new ClaimSettledEvent(ClaimId.of(CLAIM_ID), "P-1", ClaimSettlement.of(new BigDecimal("8000"),
                ClaimEnum.PayoutMethod.BANK_TRANSFER, "ACCT-1", "核赔通过"), LocalDateTime.now().minusHours(1));
    }

    // ---------- 拒赔 ----------

    @Test
    @DisplayName("PENDING 案件可拒赔，记录拒赔原因并发布拒赔事件")
    void shouldRejectClaimWhenPending() {
        fixture.given(createdEvent())
                .when(new RejectClaimCommand(ClaimId.of(CLAIM_ID), RejectReason.NOT_IN_COVERAGE, "不在保险责任范围内"))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(org.axonframework.test.matchers.Matchers
                        .payloadsMatching(org.axonframework.test.matchers.Matchers
                                .exactSequenceOf(org.hamcrest.CoreMatchers.instanceOf(ClaimRejectedEvent.class))))
                .expectState(claim -> {
                    if (claim.getStatus() != ClaimStatus.REJECTED) {
                        throw new AssertionError("拒赔后状态应为 REJECTED，实际=" + claim.getStatus());
                    }
                    if (claim.getRejectionReason() != RejectReason.NOT_IN_COVERAGE) {
                        throw new AssertionError("拒赔后应记录拒赔原因，实际=" + claim.getRejectionReason());
                    }
                });
    }

    @Test
    @DisplayName("PROCESSING 案件可拒赔")
    void shouldRejectClaimWhenProcessing() {
        fixture.given(createdEvent(), toProcessing())
                .when(new RejectClaimCommand(ClaimId.of(CLAIM_ID), RejectReason.WAITING_PERIOD, "等待期内出险"))
                .expectSuccessfulHandlerExecution()
                .expectState(claim -> {
                    if (claim.getStatus() != ClaimStatus.REJECTED) {
                        throw new AssertionError("拒赔后状态应为 REJECTED，实际=" + claim.getStatus());
                    }
                });
    }

    @Test
    @DisplayName("已核赔通过（APPROVED）不可拒赔")
    void shouldNotRejectClaimWhenApproved() {
        fixture.given(createdEvent(), toProcessing(), toApproved())
                .when(new RejectClaimCommand(ClaimId.of(CLAIM_ID), RejectReason.OTHER, "撤销"))
                .expectException(ClaimStatusPreconditionException.class);
    }

    @Test
    @DisplayName("终态（PAID）不可拒赔")
    void shouldNotRejectClaimWhenPaid() {
        fixture.given(createdEvent(), toProcessing(), toApproved(), settledEvent(),
                new com.titanium.claim.event.ClaimPaymentCompletedEvent(ClaimId.of(CLAIM_ID), "PAY-1",
                        LocalDateTime.now().minusMinutes(30)))
                .when(new RejectClaimCommand(ClaimId.of(CLAIM_ID), RejectReason.OTHER, "撤销"))
                .expectException(ClaimStatusPreconditionException.class);
    }

    @Test
    @DisplayName("已拒赔案件重复拒赔指令幂等忽略")
    void shouldIgnoreRejectWhenAlreadyRejected() {
        fixture.given(createdEvent(), new ClaimRejectedEvent(ClaimId.of(CLAIM_ID), "P-1", "C-1",
                RejectReason.FRAUD_SUSPECTED, "疑似欺诈", LocalDateTime.now().minusDays(1)))
                .when(new RejectClaimCommand(ClaimId.of(CLAIM_ID), RejectReason.FRAUD_SUSPECTED, "疑似欺诈"))
                .expectSuccessfulHandlerExecution()
                .expectNoEvents();
    }

    // ---------- 结案 ----------

    @Test
    @DisplayName("PAID 终态案件可结案归档")
    void shouldCloseClaimWhenPaid() {
        fixture.given(createdEvent(), toProcessing(), toApproved(), settledEvent(),
                new com.titanium.claim.event.ClaimPaymentCompletedEvent(ClaimId.of(CLAIM_ID), "PAY-1",
                        LocalDateTime.now().minusMinutes(30)))
                .when(new CloseClaimCommand(ClaimId.of(CLAIM_ID)))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(org.axonframework.test.matchers.Matchers
                        .payloadsMatching(org.axonframework.test.matchers.Matchers.exactSequenceOf(
                                org.hamcrest.CoreMatchers.instanceOf(com.titanium.claim.event.ClaimClosedEvent.class))))
                .expectState(claim -> {
                    if (claim.getStatus() != ClaimStatus.CLOSED) {
                        throw new AssertionError("结案后状态应为 CLOSED，实际=" + claim.getStatus());
                    }
                });
    }

    @Test
    @DisplayName("REJECTED 终态案件可结案归档")
    void shouldCloseClaimWhenRejected() {
        fixture.given(createdEvent(), new ClaimRejectedEvent(ClaimId.of(CLAIM_ID), "P-1", "C-1",
                RejectReason.UNPAID_PREMIUM, "保费未缴", LocalDateTime.now().minusDays(1)))
                .when(new CloseClaimCommand(ClaimId.of(CLAIM_ID)))
                .expectSuccessfulHandlerExecution()
                .expectState(claim -> {
                    if (claim.getStatus() != ClaimStatus.CLOSED) {
                        throw new AssertionError("结案后状态应为 CLOSED，实际=" + claim.getStatus());
                    }
                });
    }

    @Test
    @DisplayName("APPROVED（赔付中）案件不可结案")
    void shouldNotCloseClaimWhenApproved() {
        fixture.given(createdEvent(), toProcessing(), toApproved(), settledEvent())
                .when(new CloseClaimCommand(ClaimId.of(CLAIM_ID)))
                .expectException(ClaimStatusPreconditionException.class);
    }

    @Test
    @DisplayName("PENDING 案件不可结案")
    void shouldNotCloseClaimWhenPending() {
        fixture.given(createdEvent())
                .when(new CloseClaimCommand(ClaimId.of(CLAIM_ID)))
                .expectException(ClaimStatusPreconditionException.class);
    }

    // ---------- 通用状态变更通道拦截 ----------

    @Test
    @DisplayName("通用状态变更通道拦截非法流转（PENDING→PAID）")
    void shouldRejectIllegalTransitionViaGenericChannel() {
        fixture.given(createdEvent())
                .when(new ChangeClaimStatusCommand(ClaimId.of(CLAIM_ID), ClaimStatus.PAID, "直接置已支付"))
                .expectException(ClaimStatusTransitionException.class);
    }

    @Test
    @DisplayName("通用状态变更通道拦截非法流转（APPROVED→PAID 须走赔付回写）")
    void shouldRejectApprovedToPaidViaGenericChannel() {
        fixture.given(createdEvent(), toProcessing(), toApproved(), settledEvent())
                .when(new ChangeClaimStatusCommand(ClaimId.of(CLAIM_ID), ClaimStatus.PAID, "直接置已支付"))
                .expectException(ClaimStatusTransitionException.class);
    }
}
