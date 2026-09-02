package com.titanium.claim.application.saga;

import java.math.BigDecimal;
import java.util.List;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;

import com.titanium.claim.event.ClaimSettledEvent;
import com.titanium.claim.event.DeathBenefitSettledEvent;
import com.titanium.claim.port.payment.PaymentServicePort;
import com.titanium.claim.port.payment.PaymentServicePort.ClaimPayoutInstruction;
import com.titanium.claim.valueobject.BenefitCalculation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 理赔赔付集成编排（核赔结算 → 支付域 CLAIM_PAYOUT 触发点）
 * <p>
 * 监听结算事件并经 {@link PaymentServicePort} 派发赔付支付单消息（Kafka {@code payment-order-created}，
 * 由 payment 域防腐消费创建 CLAIM_PAYOUT 支付单）。结算后聚合保持 APPROVED+赔付中，支付域出账成功经
 * {@code payment-order-paid} 入站回写 {@code CompletePaymentCommand} 置 PAID，本编排不负责回写。
 * </p>
 * <p>
 * <b>形态说明</b>：单步跨服务派发、无本地多步状态与补偿需求，故用 {@code @EventHandler} 事件驱动形态
 * 而非 Axon {@code @Saga} 注解（无 StartSaga/关联键跟踪的必要）。类名保留 Saga 与领域建模文档一致。
 * 分账给付的身故支付按 {@link BenefitCalculation.BeneficiaryShare} 明细派发，收款方留空由支付域分账。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ProcessingGroup("claim-settlement-group")
public class ClaimSettlementPaymentSaga {

    private final PaymentServicePort paymentServicePort;

    /**
     * 核赔结算完成 → 派发理赔赔付支付单（普通赔付，单一收款账户）
     */
    @EventHandler
    public void on(ClaimSettledEvent event) {
        log.info("[理赔赔付] 核赔结算完成, claimId={}, 核定赔付金额={}", event.claimId(),
                event.settlement().settledAmount());
        ClaimPayoutInstruction instruction = new ClaimPayoutInstruction(event.claimId().value(), event.policyId(),
                event.settlement().settledAmount(), event.settlement().payoutMethod().getCode(),
                event.settlement().payeeAccount(), null);
        paymentServicePort.createClaimPayout(instruction);
    }

    /**
     * 身故给付结算完成 → 按受益人份额派发身故金支付单（寿险身故理赔专属）
     * <p>
     * 保单终止由 policy 域防腐监听器据本事件的 policyId 独立完成（见 claim 域 KafkaEventPublisher），
     * 本编排只负责支付集成缝，与保单终止解耦（各自幂等）。
     * </p>
     */
    @EventHandler
    public void on(DeathBenefitSettledEvent event) {
        log.info("[身故给付] 结算完成, claimId={}, policyId={}, 给付总额={}", event.claimId(), event.policyId(),
                event.settlement().settledAmount());
        List<BenefitCalculation.BeneficiaryShare> shares = event.benefitCalculation() == null ? null
                : event.benefitCalculation().shares();
        BigDecimal total = event.settlement() == null ? null : event.settlement().settledAmount();
        ClaimPayoutInstruction instruction = new ClaimPayoutInstruction(event.claimId().value(), event.policyId(),
                total, event.settlement() == null ? null : event.settlement().payoutMethod().getCode(), null, shares);
        paymentServicePort.createClaimPayout(instruction);
    }
}
