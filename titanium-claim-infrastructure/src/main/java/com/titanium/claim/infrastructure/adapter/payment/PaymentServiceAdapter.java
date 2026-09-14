package com.titanium.claim.infrastructure.adapter.payment;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;

import com.titanium.claim.common.constant.ClaimConstants;
import com.titanium.claim.port.payment.PaymentServicePort;
import com.titanium.common.kafka.KafkaPublishSupport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 支付服务 Adapter（对端域：payment）
 * <p>
 * 实现 {@link PaymentServicePort}：经 Kafka {@code payment-order-created} 主题发布理赔赔付支付单消息，
 * 由 payment 域防腐消费后创建 CLAIM_PAYOUT 类型支付单。
 * </p>
 * <p>
 * 🔴 <b>出站可靠性（m6-913）</b>：发送经 {@link KafkaPublishSupport#awaitSent} 同步等待 broker 确认，
 * 失败抛 {@code KafkaPublishException} → 调用方 {@code ClaimSettlementPaymentSaga} 所在处理组
 * {@code claim-settlement-group}（tracking + DLQ）据此入队死信，由 {@code DeadLetterQueueService} 定时重投。
 * 原先「发后即弃 future」会让 Kafka 不可达/确认超时**静默丢失**赔付指令（赔案永不付款且无痕迹）。
 * </p>
 * <p>
 * <b>重投为何不会重复出款</b>：本端口为 at-least-once，重复投递由 payment 域**双层幂等**兜底——
 * ① {@code ClaimPayoutOrchestratorImpl} 按「同赔案 + 同业务类型 + 同租户 + 在途/已成功」查重；
 * ② 建单 ID 由 {@code CP- + claimId} **确定性派生**，两次投递派生同一 paymentId，Axon 事件流已存在
 * 即建流失败（不依赖读模型最终一致的强一致兜底）。详见
 * {@code docs/技术文档/出站可靠性方案选型-2026-09.md} §8.4。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentServiceAdapter implements PaymentServicePort {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Override
    public String createClaimPayout(ClaimPayoutInstruction instruction) {
        String payload = JSON.toJSONString(instruction);
        log.info("[理赔赔付-出站] 发布赔付支付单消息: claimId={}, amount={}, 分账受益人={}",
                instruction.claimId(), instruction.amount(),
                instruction.beneficiaryShares() == null ? 0 : instruction.beneficiaryShares().size());
        KafkaPublishSupport.awaitSent(
                () -> kafkaTemplate.send(ClaimConstants.KafkaTopic.PAYMENT_ORDER_CREATED,
                        instruction.claimId(), payload),
                ClaimConstants.KafkaTopic.PAYMENT_ORDER_CREATED, instruction.claimId());
        return instruction.claimId();
    }
}
