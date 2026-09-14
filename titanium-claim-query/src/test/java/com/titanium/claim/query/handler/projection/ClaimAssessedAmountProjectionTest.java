package com.titanium.claim.query.handler.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

import com.titanium.claim.event.ClaimLossAssessedEvent;
import com.titanium.claim.query.mapper.ClaimViewMapper;
import com.titanium.claim.query.repository.ClaimViewRepository;
import com.titanium.claim.query.view.ClaimView;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.LossAssessment;
import com.titanium.metadata.enums.claim.ClaimPhase;

/**
 * 理赔读模型「按定损应赔金额」投影测试
 * <p>
 * 守护定损结果向读侧的贯通：写侧聚合据 {@code LossAssessment.payableAmount()} 核定结算金额，
 * 读侧 {@code t_claim_view.assessed_payable_amount} 必须同步落定损核定额，否则结案界面拿不到
 * 「按定损应赔金额」而只能看到结算后的实付金额——属「字段有值但恒为空」的静默失真。
 * 未定损案件必须落 {@code null} 而非 0，调用方据此区分「有定损依据」与「无依据」。
 * </p>
 */
class ClaimAssessedAmountProjectionTest {

    private static final String CLAIM_ID = "CLAIM-AA-1";

    private ClaimViewRepository       repository;
    private ClaimProjectionEventHandler handler;
    private ClaimView                 view;

    @BeforeEach
    void setUp() {
        repository = mock(ClaimViewRepository.class);
        handler = new ClaimProjectionEventHandler(repository, mock(ClaimViewMapper.class));
        view = new ClaimView();
        view.setClaimId(CLAIM_ID);
        view.setPhase(ClaimPhase.SURVEY);
        when(repository.findByClaimId(CLAIM_ID)).thenReturn(Optional.of(view));
    }

    @Test
    @DisplayName("定损提交投影：按定损应赔金额落定损核定额，阶段推进至 LOSS_ASSESS")
    void shouldProjectAssessedPayableAmount() {
        handler.on(lossAssessedEvent(normalLoss()));

        assertEquals(0, view.getAssessedPayableAmount().compareTo(new BigDecimal("6300")));
        assertEquals(ClaimPhase.LOSS_ASSESS, view.getPhase());
        verify(repository).save(view);
    }

    @Test
    @DisplayName("定损值对象缺失：落 null 而非 0，不以 0 冒充「有定损依据」")
    void shouldProjectNullWhenLossAssessmentMissing() {
        handler.on(lossAssessedEvent(null));

        assertNull(view.getAssessedPayableAmount());
        assertEquals(ClaimPhase.LOSS_ASSESS, view.getPhase());
        verify(repository).save(view);
    }

    /** 常规定损：总金额 10000 − 残值 1000 → 9000，责任比例 0.7 → 应赔 6300 */
    private LossAssessment normalLoss() {
        return new LossAssessment(new BigDecimal("10000"),
                List.of(new LossAssessment.LossItem("保险杠", new BigDecimal("6000"))), new BigDecimal("1000"),
                new BigDecimal("0.7"), "A-1");
    }

    private ClaimLossAssessedEvent lossAssessedEvent(LossAssessment lossAssessment) {
        return new ClaimLossAssessedEvent(ClaimId.of(CLAIM_ID), lossAssessment, ClaimPhase.LOSS_ASSESS,
                LocalDateTime.now());
    }
}
