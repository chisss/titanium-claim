package com.titanium.claim.infrastructure.messaging;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSONObject;

import com.titanium.claim.command.CompletePaymentCommand;
import com.titanium.claim.common.constant.ClaimConstants;
import com.titanium.claim.infrastructure.messaging.inbound.PaymentOrderPaidMessage;
import com.titanium.claim.valueobject.ClaimId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 支付结果入站消费者（对端域：payment）
 * <p>
 * 防腐消费 {@code payment-order-paid} 主题：一次反序列化入站防腐 record
 * {@link PaymentOrderPaidMessage}（不依赖对端域类型），据 businessId 派发
 * {@link CompletePaymentCommand} 回写赔案至 PAID。与赔付 Saga 的派发职责互补
 * （Saga 发出站、本消费者收回写），共同构成结算→支付的异步闭环。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentResultConsumer {

    private final CommandGateway commandGateway;

    /**
     * 监听支付出账成功消息并回写赔案赔付完成。
     */
    @KafkaListener(topics = ClaimConstants.KafkaTopic.PAYMENT_ORDER_PAID,
            groupId = "${spring.kafka.consumer.group-id}")
    public void onPaymentPaid(String payload) {
        log.debug("[支付回写-入站] 收到支付出账成功消息: {}", payload);
        try {
            PaymentOrderPaidMessage message = JSONObject.parseObject(payload, PaymentOrderPaidMessage.class);
            if (message == null || message.businessId() == null || message.paymentNo() == null) {
                log.warn("[支付回写-入站] 消息字段缺失，忽略: {}", payload);
                return;
            }
            log.info("[支付回写-入站] 回写赔案赔付完成: claimId={}, paymentNo={}", message.businessId(),
                    message.paymentNo());
            commandGateway.sendAndWait(new CompletePaymentCommand(ClaimId.of(message.businessId()),
                    message.paymentNo()));
        } catch (Exception e) {
            // 聚合根前置校验（非 APPROVED+已结算）会抛异常，属业务性拒绝；消费失败交由 Kafka 重试/DLQ 兜底
            log.error("[支付回写-入站] 回写失败（由 Kafka 重试兜底）: payload={}", payload, e);
            throw e;
        }
    }
}
