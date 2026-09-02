package com.titanium.claim.aggregate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.claim.command.CompletePaymentCommand;
import com.titanium.claim.command.SettleDisabilityBenefitCommand;
import com.titanium.claim.common.enums.BenefitSource;
import com.titanium.claim.common.enums.ClaimStatus;
import com.titanium.claim.event.ClaimCreatedEvent;
import com.titanium.claim.event.ClaimStatusChangedEvent;
import com.titanium.claim.event.DisabilityBenefitSettledEvent;
import com.titanium.claim.exception.ClaimStatusPreconditionException;
import com.titanium.claim.valueobject.BenefitCalculation;
import com.titanium.claim.valueobject.ClaimAmount;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.CustomerId;
import com.titanium.claim.valueobject.DisabilityClaimEvidence;
import com.titanium.claim.valueobject.PolicyId;
import com.titanium.metadata.enums.claim.ClaimEnum;

/**
 * 全残给付结算测试（寿险/意外险全残理赔 CLAIM-6 闭环）
 * <p>
 * 验证 {@code SettleDisabilityBenefitCommand}：仅 APPROVED 的 DISABILITY 案件、全残材料齐备、受益人份额核算
 * 完备方可给付，发布 {@link DisabilityBenefitSettledEvent}（携带 policyId 供跨域终止保单，同身故）。
 * 给付后进入赔付中（保持 APPROVED），由 {@code CompletePaymentCommand} 在支付域出账成功后回写置 PAID。
 * 事件含 {@code now()}，故用 expectSuccessfulHandlerExecution 断言执行成功，不做精确时间比对。
 * </p>
 */
class ClaimDisabilityBenefitTest {

    private FixtureConfiguration<Claim> fixture;

    private static final String CLAIM_ID = "CLAIM-DIS-1";
    private static final String POLICY_ID = "POL-DIS-1";

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Claim.class);
    }

    /** DISABILITY 类型理赔创建事件 */
    private ClaimCreatedEvent disabilityClaimCreated() {
        return new ClaimCreatedEvent(ClaimId.of(CLAIM_ID), CustomerId.of("C-1"), PolicyId.of(POLICY_ID), "CLM-DIS-001",
                ClaimEnum.ClaimType.DISABILITY, LocalDateTime.now().minusDays(3), "被保险人全残",
                ClaimAmount.of("500000"), LocalDateTime.now().minusDays(3));
    }

    /** 流转至 APPROVED（PENDING→PROCESSING→APPROVED） */
    private ClaimStatusChangedEvent toProcessing() {
        return new ClaimStatusChangedEvent(ClaimId.of(CLAIM_ID), ClaimStatus.PENDING, ClaimStatus.PROCESSING, "受理",
                LocalDateTime.now().minusDays(2));
    }

    private ClaimStatusChangedEvent toApproved() {
        return new ClaimStatusChangedEvent(ClaimId.of(CLAIM_ID), ClaimStatus.PROCESSING, ClaimStatus.APPROVED, "核赔通过",
                LocalDateTime.now().minusDays(1));
    }

    private DisabilityClaimEvidence completeEvidence() {
        return new DisabilityClaimEvidence("DC-2024-001", "一级伤残", LocalDateTime.now().minusDays(3),
                "某司法鉴定中心", "BP-001", LocalDateTime.now().minusDays(1));
    }

    private BenefitCalculation benefit() {
        return new BenefitCalculation(new BigDecimal("500000"),
                List.of(new BenefitCalculation.BeneficiaryShare("B-1", "配偶", new BigDecimal("0.6"),
                                new BigDecimal("300000")),
                        new BenefitCalculation.BeneficiaryShare("B-2", "子女", new BigDecimal("0.4"),
                                new BigDecimal("200000"))), BenefitSource.ACCOUNT_VALUE_MAX);
    }

    /** 全残给付结算命令（APPROVED + 材料齐备前提） */
    private SettleDisabilityBenefitCommand settleCommand() {
        return new SettleDisabilityBenefitCommand(ClaimId.of(CLAIM_ID), completeEvidence(), benefit(),
                ClaimEnum.PayoutMethod.BANK_TRANSFER, "全残给付核准");
    }

    @Test
    @DisplayName("APPROVED 的全残案件材料齐备可给付，发布全残给付结算事件")
    void shouldSettleDisabilityBenefitWhenApprovedAndComplete() {
        fixture.given(disabilityClaimCreated(), toProcessing(), toApproved())
                .when(settleCommand())
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(org.axonframework.test.matchers.Matchers
                        .payloadsMatching(org.axonframework.test.matchers.Matchers
                                .exactSequenceOf(org.hamcrest.CoreMatchers
                                        .instanceOf(DisabilityBenefitSettledEvent.class))));
    }

    @Test
    @DisplayName("给付后保持 APPROVED 并进入赔付中，待支付域出账回写")
    void shouldStayApprovedWithPaymentProcessingAfterSettle() {
        fixture.given(disabilityClaimCreated(), toProcessing(), toApproved())
                .when(settleCommand())
                .expectSuccessfulHandlerExecution()
                .expectState(claim -> {
                    if (claim.getStatus() != ClaimStatus.APPROVED) {
                        throw new AssertionError("全残给付后状态应保持 APPROVED，实际=" + claim.getStatus());
                    }
                    if (claim.getPaymentStatus() != ClaimEnum.PaymentStatus.PROCESSING) {
                        throw new AssertionError(
                                "全残给付后赔付状态应为 PROCESSING，实际=" + claim.getPaymentStatus());
                    }
                    if (claim.getDisabilityEvidence() == null || claim.getDisabilityBenefitCalculation() == null) {
                        throw new AssertionError("全残给付后应记录全残证据与给付核算");
                    }
                });
    }

    @Test
    @DisplayName("支付域出账成功回写后流转至 PAID 并记录支付单号")
    void shouldTransitionToPaidAfterCompletePayment() {
        fixture.given(disabilityClaimCreated(), toProcessing(), toApproved(), new DisabilityBenefitSettledEvent(
                ClaimId.of(CLAIM_ID), POLICY_ID, completeEvidence(), benefit(),
                com.titanium.claim.valueobject.ClaimSettlement.of(new BigDecimal("500000"),
                        ClaimEnum.PayoutMethod.BANK_TRANSFER, null, "全残给付核准"),
                LocalDateTime.now().minusHours(1)))
                .when(new CompletePaymentCommand(ClaimId.of(CLAIM_ID), "PAY-DIS-1"))
                .expectSuccessfulHandlerExecution()
                .expectState(claim -> {
                    if (claim.getStatus() != ClaimStatus.PAID) {
                        throw new AssertionError("赔付回写后状态应为 PAID，实际=" + claim.getStatus());
                    }
                    if (!"PAY-DIS-1".equals(claim.getPaymentNo())) {
                        throw new AssertionError("赔付回写后支付单号应为 PAY-DIS-1，实际=" + claim.getPaymentNo());
                    }
                });
    }

    @Test
    @DisplayName("未核赔通过（PROCESSING）不可全残给付")
    void shouldRejectSettleWhenNotApproved() {
        fixture.given(disabilityClaimCreated(), toProcessing())
                .when(settleCommand())
                .expectException(ClaimStatusPreconditionException.class);
    }

    @Test
    @DisplayName("全残材料不齐备（缺残疾鉴定证明）被拒绝")
    void shouldRejectSettleWhenEvidenceIncomplete() {
        DisabilityClaimEvidence incomplete = new DisabilityClaimEvidence(null, "一级伤残", LocalDateTime.now(),
                "某司法鉴定中心", "BP-001", LocalDateTime.now());
        fixture.given(disabilityClaimCreated(), toProcessing(), toApproved())
                .when(new SettleDisabilityBenefitCommand(ClaimId.of(CLAIM_ID), incomplete, benefit(),
                        ClaimEnum.PayoutMethod.BANK_TRANSFER, "全残给付核准"))
                .expectException(ClaimStatusPreconditionException.class);
    }

    @Test
    @DisplayName("非 DISABILITY 类型案件不可走全残给付")
    void shouldRejectSettleForNonDisabilityClaim() {
        ClaimCreatedEvent deathCreated = new ClaimCreatedEvent(ClaimId.of(CLAIM_ID), CustomerId.of("C-1"),
                PolicyId.of(POLICY_ID), "CLM-D-001", ClaimEnum.ClaimType.DEATH, LocalDateTime.now().minusDays(3),
                "身故", ClaimAmount.of("500000"), LocalDateTime.now().minusDays(3));
        fixture.given(deathCreated, toProcessing(), toApproved())
                .when(settleCommand())
                .expectException(ClaimStatusPreconditionException.class);
    }
}
