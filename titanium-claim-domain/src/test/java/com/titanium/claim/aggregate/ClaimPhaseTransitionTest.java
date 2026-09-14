package com.titanium.claim.aggregate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.claim.command.ChangeClaimStatusCommand;
import com.titanium.claim.command.CompletePaymentCommand;
import com.titanium.claim.command.RejectClaimCommand;
import com.titanium.claim.command.SettleClaimCommand;
import com.titanium.claim.command.SettleDeathBenefitCommand;
import com.titanium.claim.command.SettleDisabilityBenefitCommand;
import com.titanium.claim.command.SubmitLossAssessmentCommand;
import com.titanium.claim.command.SubmitSurveyCommand;
import com.titanium.claim.common.enums.BenefitSource;
import com.titanium.claim.common.enums.ClaimStatus;
import com.titanium.claim.event.ClaimCreatedEvent;
import com.titanium.claim.event.ClaimLossAssessedEvent;
import com.titanium.claim.event.ClaimSettledEvent;
import com.titanium.claim.event.ClaimStatusChangedEvent;
import com.titanium.claim.event.ClaimSurveySubmittedEvent;
import com.titanium.claim.valueobject.BenefitCalculation;
import com.titanium.claim.valueobject.ClaimAmount;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.ClaimSettlement;
import com.titanium.claim.valueobject.CustomerId;
import com.titanium.claim.valueobject.DeathClaimEvidence;
import com.titanium.claim.valueobject.DisabilityClaimEvidence;
import com.titanium.claim.valueobject.LossAssessment;
import com.titanium.claim.valueobject.PolicyId;
import com.titanium.claim.valueobject.Survey;
import com.titanium.metadata.enums.claim.ClaimEnum;
import com.titanium.metadata.enums.claim.ClaimPhase;
import com.titanium.metadata.enums.claim.RejectReason;

/**
 * 理赔处理阶段（ClaimPhase）推进测试
 * <p>
 * 锁死 {@code REPORT → SURVEY → LOSS_ASSESS → APPROVAL → SETTLEMENT → PAID} 主链与 {@code REJECTED}
 * 终态分支的阶段赋值：每段阶段只由对应业务事件推进，非 APPROVED 的通用状态流转不改变阶段，
 * 拒赔与赔付主链互斥。
 * </p>
 * <p>
 * 缺陷形态为「字段有值、无人赋值」——枚举七值齐备、读模型列与 Liquibase DDL 均存在，
 * 而聚合此前只在创建/查勘/定损三处赋值，核赔通过之后阶段永久停在 {@code LOSS_ASSESS}。
 * 事件流重放不会补救（无赋值即无赋值），故必须以测试钉死。
 * </p>
 * <p>
 * 事件含 {@code now()}，故用 {@code expectSuccessfulHandlerExecution} 断言执行成功，不做精确时间比对。
 * </p>
 */
class ClaimPhaseTransitionTest {

    private static final String CLAIM_ID = "CLAIM-PH-1";
    private static final String TENANT_ID = "T-1";
    private static final String POLICY_ID = "POL-PH-1";

    private FixtureConfiguration<Claim> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Claim.class);
    }

    // ---------- 事件 / 值对象构造 ----------

    private ClaimCreatedEvent createdEvent() {
        return createdEvent(ClaimEnum.ClaimType.MEDICAL);
    }

    private ClaimCreatedEvent createdEvent(ClaimEnum.ClaimType claimType) {
        return new ClaimCreatedEvent(ClaimId.of(CLAIM_ID), CustomerId.of("C-1"), PolicyId.of(POLICY_ID), "CLM-PH-001",
                claimType, LocalDateTime.now().minusDays(3), "住院医疗", ClaimAmount.of("8000"),
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

    private ClaimSurveySubmittedEvent surveySubmittedEvent() {
        return new ClaimSurveySubmittedEvent(ClaimId.of(CLAIM_ID), survey(), ClaimPhase.SURVEY,
                LocalDateTime.now().minusHours(3));
    }

    private ClaimSettledEvent settledEvent() {
        return new ClaimSettledEvent(ClaimId.of(CLAIM_ID), POLICY_ID,
                ClaimSettlement.of(new BigDecimal("8000"), ClaimEnum.PayoutMethod.BANK_TRANSFER, "ACCT-1", "核赔通过"),
                LocalDateTime.now().minusHours(1), TENANT_ID);
    }

    private Survey survey() {
        return new Survey("S-1", "现场勘查报告", List.of("photo1.jpg"), "属实", LocalDateTime.now().minusHours(4));
    }

    private LossAssessment lossAssessment() {
        return new LossAssessment(new BigDecimal("8000"),
                List.of(new LossAssessment.LossItem("保险杠", new BigDecimal("8000"))), null, new BigDecimal("1.0"), "A-1");
    }

    private BenefitCalculation benefit() {
        return new BenefitCalculation(new BigDecimal("8000"),
                List.of(new BenefitCalculation.BeneficiaryShare("B-1", "本人", BigDecimal.ONE, new BigDecimal("8000"))),
                BenefitSource.BASIC_SUM_INSURED);
    }

    // ---------- 主链前半段：REPORT → SURVEY → LOSS_ASSESS ----------

    @Test
    @DisplayName("理赔创建后阶段为 REPORT")
    void shouldStartAtReportPhase() {
        fixture.given(createdEvent())
                .when(new ChangeClaimStatusCommand(ClaimId.of(CLAIM_ID), ClaimStatus.PROCESSING, "受理"))
                .expectSuccessfulHandlerExecution()
                .expectState(claim -> assertEquals(ClaimPhase.REPORT, claim.getPhase()));
    }

    @Test
    @DisplayName("提交查勘后阶段推进至 SURVEY")
    void shouldAdvanceToSurveyPhase() {
        fixture.given(createdEvent())
                .when(new SubmitSurveyCommand(ClaimId.of(CLAIM_ID), survey()))
                .expectSuccessfulHandlerExecution()
                .expectState(claim -> assertEquals(ClaimPhase.SURVEY, claim.getPhase()));
    }

    @Test
    @DisplayName("提交定损后阶段推进至 LOSS_ASSESS")
    void shouldAdvanceToLossAssessPhase() {
        fixture.given(createdEvent(), surveySubmittedEvent())
                .when(new SubmitLossAssessmentCommand(ClaimId.of(CLAIM_ID), lossAssessment()))
                .expectSuccessfulHandlerExecution()
                .expectState(claim -> assertEquals(ClaimPhase.LOSS_ASSESS, claim.getPhase()));
    }

    // ---------- 主链后半段：→ APPROVAL ----------

    @Test
    @DisplayName("核赔通过（APPROVED）后阶段推进至 APPROVAL")
    void shouldAdvanceToApprovalPhase() {
        fixture.given(createdEvent(), toProcessing())
                .when(new ChangeClaimStatusCommand(ClaimId.of(CLAIM_ID), ClaimStatus.APPROVED, "核赔通过"))
                .expectSuccessfulHandlerExecution()
                .expectState(claim -> assertEquals(ClaimPhase.APPROVAL, claim.getPhase()));
    }

    @Test
    @DisplayName("非 APPROVED 的通用状态流转不改变阶段（PENDING→PROCESSING 仍为 REPORT）")
    void shouldNotAdvancePhaseOnNonApprovalStatusChange() {
        fixture.given(createdEvent())
                .when(new ChangeClaimStatusCommand(ClaimId.of(CLAIM_ID), ClaimStatus.PROCESSING, "受理"))
                .expectSuccessfulHandlerExecution()
                .expectState(claim -> assertEquals(ClaimPhase.REPORT, claim.getPhase()));
    }

    // ---------- 主链后半段：→ SETTLEMENT（通用 / 身故 / 全残三路同判据） ----------

    @Test
    @DisplayName("通用核赔结算后阶段推进至 SETTLEMENT")
    void shouldAdvanceToSettlementPhaseOnGenericSettle() {
        fixture.given(createdEvent(), toProcessing(), toApproved())
                .when(new SettleClaimCommand(ClaimId.of(CLAIM_ID), new BigDecimal("8000"),
                        ClaimEnum.PayoutMethod.BANK_TRANSFER, "ACCT-1", "核赔通过"))
                .expectSuccessfulHandlerExecution()
                .expectState(claim -> assertEquals(ClaimPhase.SETTLEMENT, claim.getPhase()));
    }

    @Test
    @DisplayName("身故给付结算后阶段推进至 SETTLEMENT")
    void shouldAdvanceToSettlementPhaseOnDeathBenefitSettle() {
        DeathClaimEvidence evidence = new DeathClaimEvidence("DC-2024-001", LocalDateTime.now().minusDays(3), "疾病身故",
                true, "BP-001", LocalDateTime.now().minusDays(1));

        fixture.given(createdEvent(ClaimEnum.ClaimType.DEATH), toProcessing(), toApproved())
                .when(new SettleDeathBenefitCommand(ClaimId.of(CLAIM_ID), evidence, benefit(),
                        ClaimEnum.PayoutMethod.BANK_TRANSFER, "身故给付核准"))
                .expectSuccessfulHandlerExecution()
                .expectState(claim -> assertEquals(ClaimPhase.SETTLEMENT, claim.getPhase()));
    }

    @Test
    @DisplayName("全残给付结算后阶段推进至 SETTLEMENT")
    void shouldAdvanceToSettlementPhaseOnDisabilityBenefitSettle() {
        DisabilityClaimEvidence evidence = new DisabilityClaimEvidence("DB-2024-001", "一级",
                LocalDateTime.now().minusDays(3), "市劳动能力鉴定中心", "BP-001", LocalDateTime.now().minusDays(1));

        fixture.given(createdEvent(ClaimEnum.ClaimType.DISABILITY), toProcessing(), toApproved())
                .when(new SettleDisabilityBenefitCommand(ClaimId.of(CLAIM_ID), evidence, benefit(),
                        ClaimEnum.PayoutMethod.BANK_TRANSFER, "全残给付核准"))
                .expectSuccessfulHandlerExecution()
                .expectState(claim -> assertEquals(ClaimPhase.SETTLEMENT, claim.getPhase()));
    }

    // ---------- 主链末段与分支终态：→ PAID / REJECTED ----------

    @Test
    @DisplayName("赔付完成回写后阶段推进至 PAID（主链终态）")
    void shouldAdvanceToPaidPhase() {
        fixture.given(createdEvent(), toProcessing(), toApproved(), settledEvent())
                .when(new CompletePaymentCommand(ClaimId.of(CLAIM_ID), "PAY-1"))
                .expectSuccessfulHandlerExecution()
                .expectState(claim -> assertEquals(ClaimPhase.PAID, claim.getPhase()));
    }

    @Test
    @DisplayName("拒赔后阶段推进至 REJECTED（分支终态，与赔付主链互斥）")
    void shouldAdvanceToRejectedPhase() {
        fixture.given(createdEvent(), toProcessing())
                .when(new RejectClaimCommand(ClaimId.of(CLAIM_ID), RejectReason.NOT_IN_COVERAGE, "不在保险责任范围内"))
                .expectSuccessfulHandlerExecution()
                .expectState(claim -> assertEquals(ClaimPhase.REJECTED, claim.getPhase()));
    }

    // ---------- 全链回归 ----------

    @Test
    @DisplayName("全链条贯通：查勘→定损→核赔通过→结算→赔付完成，阶段单向推进不停滞")
    void shouldWalkWholePhaseChain() {
        // 查勘：REPORT → SURVEY
        fixture.given(createdEvent())
                .when(new SubmitSurveyCommand(ClaimId.of(CLAIM_ID), survey()))
                .expectSuccessfulHandlerExecution()
                .expectState(claim -> assertEquals(ClaimPhase.SURVEY, claim.getPhase()));

        // 定损：SURVEY → LOSS_ASSESS
        fixture.given(createdEvent(), surveySubmittedEvent())
                .when(new SubmitLossAssessmentCommand(ClaimId.of(CLAIM_ID), lossAssessment()))
                .expectSuccessfulHandlerExecution()
                .expectState(claim -> assertEquals(ClaimPhase.LOSS_ASSESS, claim.getPhase()));

        // 核赔通过：LOSS_ASSESS → APPROVAL（后半段起点，此前停滞于此）
        fixture.given(createdEvent(), surveySubmittedEvent(),
                        new ClaimLossAssessedEvent(ClaimId.of(CLAIM_ID), lossAssessment(), ClaimPhase.LOSS_ASSESS,
                                LocalDateTime.now().minusHours(2)),
                        toProcessing())
                .when(new ChangeClaimStatusCommand(ClaimId.of(CLAIM_ID), ClaimStatus.APPROVED, "核赔通过"))
                .expectSuccessfulHandlerExecution()
                .expectState(claim -> assertEquals(ClaimPhase.APPROVAL, claim.getPhase()));

        // 结算：APPROVAL → SETTLEMENT
        fixture.given(createdEvent(), toProcessing(), toApproved())
                .when(new SettleClaimCommand(ClaimId.of(CLAIM_ID), new BigDecimal("8000"),
                        ClaimEnum.PayoutMethod.BANK_TRANSFER, "ACCT-1", "核赔通过"))
                .expectSuccessfulHandlerExecution()
                .expectState(claim -> assertEquals(ClaimPhase.SETTLEMENT, claim.getPhase()));

        // 赔付完成：SETTLEMENT → PAID
        fixture.given(createdEvent(), toProcessing(), toApproved(), settledEvent())
                .when(new CompletePaymentCommand(ClaimId.of(CLAIM_ID), "PAY-1"))
                .expectSuccessfulHandlerExecution()
                .expectState(claim -> assertEquals(ClaimPhase.PAID, claim.getPhase()));
    }
}
