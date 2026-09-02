package com.titanium.claim.infrastructure.adapter.payment;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;

import com.titanium.claim.common.constant.ClaimConstants;
import com.titanium.claim.port.payment.PaymentServicePort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 支付服务 Adapter（对端域：payment）
 * <p>
 * 实现 {@link PaymentServicePort}：经 Kafka {@code payment-order-created} 主题发布理赔赔付支付单消息，
 * 由 payment 域防腐消费后创建 CLAIM_PAYOUT 类型支付单。payment 域当前为待开发模块，
 * 消息契约（{@code ClaimPayoutInstruction} 序列化载荷）先行落地，供其入站监听器按本契约消费。
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
        kafkaTemplate.send(ClaimConstants.KafkaTopic.PAYMENT_ORDER_CREATED, instruction.claimId(), payload);
        return instruction.claimId();
    }
}
