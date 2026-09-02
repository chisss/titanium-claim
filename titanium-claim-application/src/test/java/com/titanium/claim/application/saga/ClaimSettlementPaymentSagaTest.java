package com.titanium.claim.application.saga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.titanium.claim.common.enums.BenefitSource;
import com.titanium.claim.event.ClaimSettledEvent;
import com.titanium.claim.event.DeathBenefitSettledEvent;
import com.titanium.claim.port.payment.PaymentServicePort;
import com.titanium.claim.port.payment.PaymentServicePort.ClaimPayoutInstruction;
import com.titanium.claim.valueobject.BenefitCalculation;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.ClaimSettlement;
import com.titanium.claim.valueobject.DeathClaimEvidence;
import com.titanium.metadata.enums.claim.ClaimEnum;

/**
 * 理赔赔付集成编排测试
 * <p>
 * 验证 {@link ClaimSettlementPaymentSaga}：监听核赔结算/身故给付结算事件并经
 * {@link PaymentServicePort} 派发赔付支付单指令（金额/给付方式/受益人分账明细正确装配）。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ClaimSettlementPaymentSagaTest {

    @Mock
    private PaymentServicePort paymentServicePort;

    private ClaimSettlementPaymentSaga saga;

    @BeforeEach
    void setUp() {
        saga = new ClaimSettlementPaymentSaga(paymentServicePort);
    }

    @Test
    @DisplayName("核赔结算事件 → 派发普通赔付支付单（单一收款账户）")
    void shouldDispatchPayoutOnClaimSettled() {
        ClaimSettlement settlement = ClaimSettlement.of(new BigDecimal("8000"),
                ClaimEnum.PayoutMethod.BANK_TRANSFER, "ACCT-1", "核赔通过");
        saga.on(new ClaimSettledEvent(ClaimId.of("CLAIM-1"), "POL-1", settlement, LocalDateTime.now()));

        ArgumentCaptor<ClaimPayoutInstruction> captor = ArgumentCaptor.forClass(ClaimPayoutInstruction.class);
        verify(paymentServicePort).createClaimPayout(captor.capture());
        ClaimPayoutInstruction instruction = captor.getValue();
        assertEquals("CLAIM-1", instruction.claimId());
        assertEquals("POL-1", instruction.policyId());
        assertEquals(new BigDecimal("8000"), instruction.amount());
        assertEquals(ClaimEnum.PayoutMethod.BANK_TRANSFER.getCode(), instruction.payoutMethodCode());
        assertEquals("ACCT-1", instruction.payeeAccount());
        assertNull(instruction.beneficiaryShares(), "普通赔付不应携带分账明细");
    }

    @Test
    @DisplayName("身故给付结算事件 → 按受益人份额派发分账支付单（收款方留空）")
    void shouldDispatchPayoutWithSharesOnDeathBenefitSettled() {
        BenefitCalculation calculation = new BenefitCalculation(new BigDecimal("500000"),
                List.of(new BenefitCalculation.BeneficiaryShare("B-1", "配偶", new BigDecimal("0.6"),
                                new BigDecimal("300000")),
                        new BenefitCalculation.BeneficiaryShare("B-2", "子女", new BigDecimal("0.4"),
                                new BigDecimal("200000"))), BenefitSource.BASIC_SUM_INSURED);
        ClaimSettlement settlement = ClaimSettlement.of(new BigDecimal("500000"),
                ClaimEnum.PayoutMethod.BANK_TRANSFER, null, "身故给付核准");
        saga.on(new DeathBenefitSettledEvent(ClaimId.of("CLAIM-2"), "POL-2",
                new DeathClaimEvidence("DC-1", LocalDateTime.now(), "疾病", true, "BP-1", LocalDateTime.now()),
                calculation, settlement, LocalDateTime.now()));

        ArgumentCaptor<ClaimPayoutInstruction> captor = ArgumentCaptor.forClass(ClaimPayoutInstruction.class);
        verify(paymentServicePort).createClaimPayout(captor.capture());
        ClaimPayoutInstruction instruction = captor.getValue();
        assertEquals("CLAIM-2", instruction.claimId());
        assertEquals("POL-2", instruction.policyId());
        assertEquals(new BigDecimal("500000"), instruction.amount());
        assertNull(instruction.payeeAccount(), "分账给付收款方留空由支付域按明细分账");
        assertEquals(2, instruction.beneficiaryShares().size());
        assertTrue(instruction.beneficiaryShares().stream()
                .anyMatch(s -> "B-1".equals(s.beneficiaryId()) && s.amount().compareTo(new BigDecimal("300000")) == 0));
    }
}
