package com.titanium.claim.aggregate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.claim.command.SettleClaimCommand;
import com.titanium.claim.common.enums.ClaimStatus;
import com.titanium.claim.event.ClaimCreatedEvent;
import com.titanium.claim.event.ClaimLossAssessedEvent;
import com.titanium.claim.event.ClaimStatusChangedEvent;
import com.titanium.claim.event.ClaimSurveySubmittedEvent;
import com.titanium.claim.exception.ClaimSettlementAmountException;
import com.titanium.claim.exception.ClaimStatusPreconditionException;
import com.titanium.claim.valueobject.ClaimAmount;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.CustomerId;
import com.titanium.claim.valueobject.LossAssessment;
import com.titanium.claim.valueobject.PolicyId;
import com.titanium.claim.valueobject.Survey;
import com.titanium.metadata.enums.claim.ClaimEnum;
import com.titanium.metadata.enums.claim.ClaimPhase;

/**
 * 核赔结算金额核定测试（定损结果 → 核赔结算接线）
 * <p>
 * 锁死判据——核定赔付金额存在**唯一权威来源**，调用方不得透传改数：
 * <ol>
 *   <li>定损在案时金额取 {@code LossAssessment.payableAmount()}（=（定损总金额−残值）×责任比例），传空即取核定额；</li>
 *   <li>定损在案时传值与核定额不等（无论高低）一律拒绝，人工改数通道堵死；</li>
 *   <li>定损核定额非正（残值吃掉全部损失）时拒绝，不得以 0 元结案；</li>
 *   <li>未定损案件仍走原有显式金额路径（向后兼容），但金额缺失即拒绝——该兜底自 web 层 {@code @NotNull}
 *       下沉至聚合，任何入口（Feign / 内部调用）都无法绕过。</li>
 * </ol>
 * 与身故给付「禁止透传金额、由基本保额精算」同源（CLAIM-2）：赔付金额始终须有可追溯的精算依据。
 * </p>
 */
class ClaimSettlementAmountTest {

    private static final String CLAIM_ID  = "CLAIM-SA-1";
    private static final String TENANT_ID = "T-1";

    /** 定损：总金额 10000、残值 1000、责任比例 0.7 → 核定额 (10000−1000)×0.7 = 6300 */
    private static final BigDecimal ASSESSED_TOTAL   = new BigDecimal("10000");
    private static final BigDecimal SALVAGE_VALUE    = new BigDecimal("1000");
    private static final BigDecimal LIABILITY_RATIO  = new BigDecimal("0.7");
    private static final BigDecimal ASSESSED_PAYABLE = new BigDecimal("6300");

    private FixtureConfiguration<Claim> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Claim.class);
    }

    @Test
    @DisplayName("定损在案：传空即取定损核定额")
    void shouldDeriveSettledAmountFromLossAssessmentWhenAmountOmitted() {
        fixture.given(createdEvent(), surveySubmittedEvent(), lossAssessedEvent(normalLoss()), toProcessing(),
                        toApproved())
                .when(settleCommand(null))
                .expectState(claim -> assertEquals(0,
                        claim.getSettlement().settledAmount().compareTo(ASSESSED_PAYABLE)));
    }

    @Test
    @DisplayName("定损在案：传值与核定额相等时放行（显式路径向后兼容）")
    void shouldAcceptProvidedAmountEqualToAssessedAmount() {
        fixture.given(createdEvent(), surveySubmittedEvent(), lossAssessedEvent(normalLoss()), toProcessing(),
                        toApproved())
                .when(settleCommand(ASSESSED_PAYABLE))
                .expectState(claim -> assertEquals(0,
                        claim.getSettlement().settledAmount().compareTo(ASSESSED_PAYABLE)));
    }

    @Test
    @DisplayName("定损在案：传值高于核定额（超额赔付）被拒绝")
    void shouldRejectProvidedAmountHigherThanAssessedAmount() {
        fixture.given(createdEvent(), surveySubmittedEvent(), lossAssessedEvent(normalLoss()), toProcessing(),
                        toApproved())
                .when(settleCommand(new BigDecimal("9000")))
                .expectException(ClaimSettlementAmountException.class);
    }

    @Test
    @DisplayName("定损在案：传值低于核定额（克扣赔付）同样被拒绝")
    void shouldRejectProvidedAmountLowerThanAssessedAmount() {
        fixture.given(createdEvent(), surveySubmittedEvent(), lossAssessedEvent(normalLoss()), toProcessing(),
                        toApproved())
                .when(settleCommand(new BigDecimal("6000")))
                .expectException(ClaimSettlementAmountException.class);
    }

    @Test
    @DisplayName("定损核定额非正（残值扣尽损失）时拒绝结算，不得以 0 元结案")
    void shouldRejectWhenAssessedAmountIsNotPositive() {
        fixture.given(createdEvent(), surveySubmittedEvent(), lossAssessedEvent(zeroPayableLoss()), toProcessing(),
                        toApproved())
                .when(settleCommand(null))
                .expectException(ClaimSettlementAmountException.class);
    }

    @Test
    @DisplayName("未定损：金额缺失即拒绝（兜底自 web 层下沉至聚合）")
    void shouldRejectWhenNotAssessedAndAmountMissing() {
        fixture.given(createdEvent(), toProcessing(), toApproved())
                .when(settleCommand(null))
                .expectException(ClaimSettlementAmountException.class);
    }

    @Test
    @DisplayName("未定损：显式金额路径保持可用（向后兼容）")
    void shouldAcceptExplicitAmountWhenNotAssessed() {
        fixture.given(createdEvent(), toProcessing(), toApproved())
                .when(settleCommand(new BigDecimal("5000")))
                .expectState(claim -> assertEquals(0, claim.getSettlement().settledAmount()
                        .compareTo(new BigDecimal("5000"))));
    }

    @Test
    @DisplayName("定损在案：对外暴露的按定损应赔金额与核定额一致")
    void shouldExposeAssessedPayableAmountWhenAssessed() {
        fixture.given(createdEvent(), surveySubmittedEvent(), lossAssessedEvent(normalLoss()), toProcessing(),
                        toApproved())
                .when(settleCommand(null))
                .expectState(claim -> assertEquals(0, claim.assessedPayableAmount().compareTo(ASSESSED_PAYABLE)));
    }

    @Test
    @DisplayName("未定损：按定损应赔金额返回 null，不以 0 冒充「有定损依据」")
    void shouldExposeNullAssessedPayableAmountWhenNotAssessed() {
        fixture.given(createdEvent(), toProcessing(), toApproved())
                .when(settleCommand(new BigDecimal("5000")))
                .expectState(claim -> assertNull(claim.assessedPayableAmount()));
    }

    @Test
    @DisplayName("状态不满足（未核赔通过）时结算被前置拒绝，与金额核定无关")
    void shouldRejectSettlementWhenStatusNotApproved() {
        fixture.given(createdEvent(), surveySubmittedEvent(), lossAssessedEvent(normalLoss()), toProcessing())
                .when(settleCommand(null))
                .expectException(ClaimStatusPreconditionException.class);
    }

    private SettleClaimCommand settleCommand(BigDecimal settledAmount) {
        return new SettleClaimCommand(ClaimId.of(CLAIM_ID), settledAmount, ClaimEnum.PayoutMethod.BANK_TRANSFER,
                "ACCT-1", "核赔通过");
    }

    private ClaimCreatedEvent createdEvent() {
        return new ClaimCreatedEvent(ClaimId.of(CLAIM_ID), CustomerId.of("C-1"), PolicyId.of("P-1"), "CLM-SA-001",
                ClaimEnum.ClaimType.PROPERTY, LocalDateTime.now().minusDays(3), "车辆碰撞", ClaimAmount.of("20000"),
                LocalDateTime.now().minusDays(3), TENANT_ID);
    }

    private ClaimSurveySubmittedEvent surveySubmittedEvent() {
        return new ClaimSurveySubmittedEvent(ClaimId.of(CLAIM_ID),
                new Survey("S-1", "现场勘查报告", List.of("photo1.jpg"), "属实", LocalDateTime.now().minusDays(2)),
                ClaimPhase.SURVEY, LocalDateTime.now().minusDays(2));
    }

    private ClaimLossAssessedEvent lossAssessedEvent(LossAssessment lossAssessment) {
        return new ClaimLossAssessedEvent(ClaimId.of(CLAIM_ID), lossAssessment, ClaimPhase.LOSS_ASSESS,
                LocalDateTime.now().minusDays(1));
    }

    /** 常规定损：总金额 10000 − 残值 1000 → 9000，责任比例 0.7 → 应赔 6300 */
    private LossAssessment normalLoss() {
        return new LossAssessment(ASSESSED_TOTAL,
                List.of(new LossAssessment.LossItem("保险杠", new BigDecimal("6000"))), SALVAGE_VALUE,
                LIABILITY_RATIO, "A-1");
    }

    /** 残值扣尽损失：总金额 1000 − 残值 1000 → 0，核定额非正 */
    private LossAssessment zeroPayableLoss() {
        return new LossAssessment(new BigDecimal("1000"),
                List.of(new LossAssessment.LossItem("划痕修复", new BigDecimal("1000"))), new BigDecimal("1000"),
                BigDecimal.ONE, "A-1");
    }

    private ClaimStatusChangedEvent toProcessing() {
        return new ClaimStatusChangedEvent(ClaimId.of(CLAIM_ID), ClaimStatus.PENDING, ClaimStatus.PROCESSING, "受理",
                LocalDateTime.now().minusHours(2), TENANT_ID);
    }

    private ClaimStatusChangedEvent toApproved() {
        return new ClaimStatusChangedEvent(ClaimId.of(CLAIM_ID), ClaimStatus.PROCESSING, ClaimStatus.APPROVED,
                "核赔通过", LocalDateTime.now().minusHours(1), TENANT_ID);
    }
}
