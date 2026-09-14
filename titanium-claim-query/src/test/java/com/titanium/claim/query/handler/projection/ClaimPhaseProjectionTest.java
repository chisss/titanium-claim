package com.titanium.claim.query.handler.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.claim.common.enums.BenefitSource;
import com.titanium.claim.common.enums.ClaimStatus;
import com.titanium.claim.event.ClaimPaymentCompletedEvent;
import com.titanium.claim.event.ClaimRejectedEvent;
import com.titanium.claim.event.ClaimSettledEvent;
import com.titanium.claim.event.ClaimStatusChangedEvent;
import com.titanium.claim.event.DeathBenefitSettledEvent;
import com.titanium.claim.event.DisabilityBenefitSettledEvent;
import com.titanium.claim.query.mapper.ClaimViewMapper;
import com.titanium.claim.query.repository.ClaimViewRepository;
import com.titanium.claim.query.view.ClaimView;
import com.titanium.claim.valueobject.BenefitCalculation;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.ClaimSettlement;
import com.titanium.claim.valueobject.DeathClaimEvidence;
import com.titanium.claim.valueobject.DisabilityClaimEvidence;
import com.titanium.metadata.enums.claim.ClaimEnum;
import com.titanium.metadata.enums.claim.ClaimPhase;
import com.titanium.metadata.enums.claim.RejectReason;

/**
 * 理赔读模型阶段（phase）投影测试
 * <p>
 * 守护 CQRS 读写两侧阶段推进判据一致：写侧聚合在核赔通过 / 结算 / 拒赔 / 赔付完成四处推进 {@code phase}，
 * 读侧投影必须同步推进，否则 {@code t_claim_view.phase} 会永久停在 {@code LOSS_ASSESS}——
 * 而查询接口照常返回该字段，属「字段有值但恒为旧值」的静默失真。
 * </p>
 */
class ClaimPhaseProjectionTest {

    private static final String CLAIM_ID = "CLAIM-PH-P1";
    private static final String TENANT_ID = "T-1";

    private ClaimViewRepository       repository;
    private ClaimProjectionEventHandler handler;
    private ClaimView                 view;

    @BeforeEach
    void setUp() {
        repository = mock(ClaimViewRepository.class);
        handler = new ClaimProjectionEventHandler(repository, mock(ClaimViewMapper.class));
        view = new ClaimView();
        view.setClaimId(CLAIM_ID);
        view.setPhase(ClaimPhase.LOSS_ASSESS); // 后半段起点：此前实现中阶段停滞于此
        when(repository.findByClaimId(CLAIM_ID)).thenReturn(Optional.of(view));
    }

    private ClaimSettlement settlement() {
        return ClaimSettlement.of(new BigDecimal("8000"), ClaimEnum.PayoutMethod.BANK_TRANSFER, "ACCT-1", "核赔通过");
    }

    private BenefitCalculation benefit() {
        return new BenefitCalculation(new BigDecimal("8000"),
                List.of(new BenefitCalculation.BeneficiaryShare("B-1", "本人", BigDecimal.ONE, new BigDecimal("8000"))),
                BenefitSource.BASIC_SUM_INSURED);
    }

    private void assertPhaseAndSaved(ClaimPhase expected) {
        assertEquals(expected, view.getPhase());
        verify(repository).save(view);
    }

    @Test
    @DisplayName("状态变更投影：核赔通过推进 phase 至 APPROVAL")
    void shouldAdvanceToApprovalOnStatusChanged() {
        handler.on(new ClaimStatusChangedEvent(ClaimId.of(CLAIM_ID), ClaimStatus.PROCESSING, ClaimStatus.APPROVED,
                "核赔通过", LocalDateTime.now(), TENANT_ID));

        assertPhaseAndSaved(ClaimPhase.APPROVAL);
    }

    @Test
    @DisplayName("状态变更投影：非 APPROVED 流转不推进 phase")
    void shouldNotAdvanceOnNonApprovalStatusChanged() {
        handler.on(new ClaimStatusChangedEvent(ClaimId.of(CLAIM_ID), ClaimStatus.PENDING, ClaimStatus.PROCESSING,
                "受理", LocalDateTime.now(), TENANT_ID));

        assertPhaseAndSaved(ClaimPhase.LOSS_ASSESS);
    }

    @Test
    @DisplayName("通用核赔结算投影：phase 推进至 SETTLEMENT")
    void shouldAdvanceToSettlementOnGenericSettle() {
        handler.on(new ClaimSettledEvent(ClaimId.of(CLAIM_ID), "P-1", settlement(), LocalDateTime.now(), TENANT_ID));

        assertPhaseAndSaved(ClaimPhase.SETTLEMENT);
    }

    @Test
    @DisplayName("身故给付结算投影：phase 推进至 SETTLEMENT")
    void shouldAdvanceToSettlementOnDeathBenefitSettle() {
        DeathClaimEvidence evidence = new DeathClaimEvidence("DC-1", LocalDateTime.now().minusDays(3), "疾病身故", true,
                "BP-1", LocalDateTime.now().minusDays(1));
        handler.on(new DeathBenefitSettledEvent(ClaimId.of(CLAIM_ID), "P-1", evidence, benefit(), settlement(),
                LocalDateTime.now(), TENANT_ID));

        assertPhaseAndSaved(ClaimPhase.SETTLEMENT);
    }

    @Test
    @DisplayName("全残给付结算投影：phase 推进至 SETTLEMENT")
    void shouldAdvanceToSettlementOnDisabilityBenefitSettle() {
        DisabilityClaimEvidence evidence = new DisabilityClaimEvidence("DB-1", "一级", LocalDateTime.now().minusDays(3),
                "市劳动能力鉴定中心", "BP-1", LocalDateTime.now().minusDays(1));
        handler.on(new DisabilityBenefitSettledEvent(ClaimId.of(CLAIM_ID), "P-1", evidence, benefit(), settlement(),
                LocalDateTime.now(), TENANT_ID));

        assertPhaseAndSaved(ClaimPhase.SETTLEMENT);
    }

    @Test
    @DisplayName("拒赔投影：phase 推进至 REJECTED")
    void shouldAdvanceToRejectedOnReject() {
        handler.on(new ClaimRejectedEvent(ClaimId.of(CLAIM_ID), "P-1", "C-1", RejectReason.NOT_IN_COVERAGE, "不在责任范围",
                LocalDateTime.now(), TENANT_ID));

        assertPhaseAndSaved(ClaimPhase.REJECTED);
    }

    @Test
    @DisplayName("赔付完成投影：phase 推进至 PAID")
    void shouldAdvanceToPaidOnPaymentCompleted() {
        handler.on(new ClaimPaymentCompletedEvent(ClaimId.of(CLAIM_ID), "PAY-1", LocalDateTime.now()));

        assertPhaseAndSaved(ClaimPhase.PAID);
    }
}
