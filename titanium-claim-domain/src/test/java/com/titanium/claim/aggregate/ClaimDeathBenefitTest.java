package com.titanium.claim.aggregate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.claim.command.SettleDeathBenefitCommand;
import com.titanium.claim.common.enums.ClaimStatus;
import com.titanium.claim.event.ClaimCreatedEvent;
import com.titanium.claim.event.ClaimStatusChangedEvent;
import com.titanium.claim.event.DeathBenefitSettledEvent;
import com.titanium.claim.exception.ClaimStatusPreconditionException;
import com.titanium.claim.valueobject.BenefitCalculation;
import com.titanium.claim.valueobject.ClaimAmount;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.CustomerId;
import com.titanium.claim.valueobject.DeathClaimEvidence;
import com.titanium.claim.valueobject.PolicyId;
import com.titanium.metadata.enums.claim.ClaimEnum;

/**
 * 身故给付结算测试（寿险身故理赔 P0-3 闭环）
 * <p>
 * 验证 {@code SettleDeathBenefitCommand}：仅 APPROVED 的 DEATH 案件、身故材料齐备、受益人份额核算完备
 * 方可给付，发布 {@link DeathBenefitSettledEvent}（携带 policyId 供跨域终止保单）。身故给付事件含
 * {@code now()}，故用 expectSuccessfulHandlerExecution 断言执行成功，不做精确时间比对。
 * </p>
 */
class ClaimDeathBenefitTest {

    private FixtureConfiguration<Claim> fixture;

    private static final String CLAIM_ID = "CLAIM-D-1";
    private static final String POLICY_ID = "POL-D-1";

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Claim.class);
    }

    /** DEATH 类型理赔创建事件 */
    private ClaimCreatedEvent deathClaimCreated() {
        return new ClaimCreatedEvent(ClaimId.of(CLAIM_ID), CustomerId.of("C-1"), PolicyId.of(POLICY_ID), "CLM-D-001",
                ClaimEnum.ClaimType.DEATH, LocalDateTime.now().minusDays(3), "被保险人身故", ClaimAmount.of("500000"),
                LocalDateTime.now().minusDays(3));
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

    private DeathClaimEvidence completeEvidence() {
        return new DeathClaimEvidence("DC-2024-001", LocalDateTime.now().minusDays(3), "疾病身故", true, "BP-001",
                LocalDateTime.now().minusDays(1));
    }

    private BenefitCalculation benefit() {
        return new BenefitCalculation(new BigDecimal("500000"),
                List.of(new BenefitCalculation.BeneficiaryShare("B-1", "配偶", new BigDecimal("0.6"),
                                new BigDecimal("300000")),
                        new BenefitCalculation.BeneficiaryShare("B-2", "子女", new BigDecimal("0.4"),
                                new BigDecimal("200000"))));
    }

    @Test
    @DisplayName("APPROVED 的身故案件材料齐备可给付，发布身故给付结算事件")
    void shouldSettleDeathBenefitWhenApprovedAndComplete() {
        fixture.given(deathClaimCreated(), toProcessing(), toApproved())
                .when(new SettleDeathBenefitCommand(ClaimId.of(CLAIM_ID), completeEvidence(), benefit(),
                        ClaimEnum.PayoutMethod.BANK_TRANSFER, "身故给付核准"))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(org.axonframework.test.matchers.Matchers
                        .payloadsMatching(org.axonframework.test.matchers.Matchers
                                .exactSequenceOf(org.hamcrest.CoreMatchers.instanceOf(DeathBenefitSettledEvent.class))));
    }

    @Test
    @DisplayName("给付后理赔状态流转至 PAID")
    void shouldTransitionToPaidAfterSettle() {
        fixture.given(deathClaimCreated(), toProcessing(), toApproved())
                .when(new SettleDeathBenefitCommand(ClaimId.of(CLAIM_ID), completeEvidence(), benefit(),
                        ClaimEnum.PayoutMethod.BANK_TRANSFER, "身故给付核准"))
                .expectSuccessfulHandlerExecution()
                .expectState(claim -> {
                    if (claim.getStatus() != ClaimStatus.PAID) {
                        throw new AssertionError("身故给付后状态应为 PAID，实际=" + claim.getStatus());
                    }
                });
    }

    @Test
    @DisplayName("未核赔通过（PROCESSING）不可身故给付")
    void shouldRejectSettleWhenNotApproved() {
        fixture.given(deathClaimCreated(), toProcessing())
                .when(new SettleDeathBenefitCommand(ClaimId.of(CLAIM_ID), completeEvidence(), benefit(),
                        ClaimEnum.PayoutMethod.BANK_TRANSFER, "身故给付核准"))
                .expectException(ClaimStatusPreconditionException.class);
    }

    @Test
    @DisplayName("身故材料不齐备（缺死亡证明）被拒绝")
    void shouldRejectSettleWhenEvidenceIncomplete() {
        DeathClaimEvidence incomplete = new DeathClaimEvidence(null, LocalDateTime.now(), "疾病", false, "BP-001",
                LocalDateTime.now());
        fixture.given(deathClaimCreated(), toProcessing(), toApproved())
                .when(new SettleDeathBenefitCommand(ClaimId.of(CLAIM_ID), incomplete, benefit(),
                        ClaimEnum.PayoutMethod.BANK_TRANSFER, "身故给付核准"))
                .expectException(ClaimStatusPreconditionException.class);
    }

    @Test
    @DisplayName("非 DEATH 类型案件不可走身故给付")
    void shouldRejectSettleForNonDeathClaim() {
        ClaimCreatedEvent medicalCreated = new ClaimCreatedEvent(ClaimId.of(CLAIM_ID), CustomerId.of("C-1"),
                PolicyId.of(POLICY_ID), "CLM-M-001", ClaimEnum.ClaimType.MEDICAL, LocalDateTime.now().minusDays(3),
                "医疗", ClaimAmount.of("500000"), LocalDateTime.now().minusDays(3));
        fixture.given(medicalCreated, toProcessing(), toApproved())
                .when(new SettleDeathBenefitCommand(ClaimId.of(CLAIM_ID), completeEvidence(), benefit(),
                        ClaimEnum.PayoutMethod.BANK_TRANSFER, "身故给付核准"))
                .expectException(ClaimStatusPreconditionException.class);
    }
}
