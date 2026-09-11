package com.titanium.claim.infrastructure.messaging;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSONObject;

import com.titanium.claim.application.orchestration.payment.PaymentCompletionOrchestrator;
import com.titanium.claim.common.constant.ClaimConstants;
import com.titanium.claim.infrastructure.messaging.inbound.PaymentOrderFailedMessage;
import com.titanium.claim.infrastructure.messaging.inbound.PaymentOrderPaidMessage;
import com.titanium.metadata.enums.BusinessDomainType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 支付结果入站消费者（对端域：payment，Kafka 入站适配器 / 防腐层）
 * <p>
 * 防腐消费支付域两条出账结果主题：{@code payment-order-paid}（出账成功）与
 * {@code payment-order-failed}（出账未成功：渠道确认失败 / 人工取消）。两者各自一次反序列化入站防腐
 * record（不依赖对端域类型），据 businessId 委托应用层 {@link PaymentCompletionOrchestrator} 回写赔案——
 * 前者置 PAID，后者标记赔付失败。与赔付 Saga 的派发职责互补（Saga 发出站、本消费者收回写），
 * 共同构成结算→支付的异步闭环。
 * </p>
 * <p>
 * <b>归属 infrastructure（driving adapter）</b>：{@code @KafkaListener} 只做消息接入与防腐翻译，
 * 发命令的编排逻辑下沉 application（{@link PaymentCompletionOrchestrator}），
 * infrastructure 不持有 CommandGateway（ArchUnit 固化）。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentResultConsumer {

    private final PaymentCompletionOrchestrator paymentCompletionOrchestrator;

    /**
     * 监听支付出账成功消息并回写赔案赔付完成。
     * <p>
     * 🔴 <b>必须按 {@code businessType} 过滤本域消息</b>：{@code payment-order-paid} 是支付域对
     * **所有**业务域出账成功的统一出口（保单域保费收取、保全域退费同样发布到本主题）。不过滤则
     * 保费支付成功的 {@code businessId}（保单ID）会被当作赔案ID 回写，赔案不存在 → 抛异常重抛 →
     * 消息被无限重放。域外消息静默跳过，不记 ERROR（属正常流量，非故障）。
     * </p>
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
            if (!BusinessDomainType.CLAIM.getCode().equals(message.businessType())) {
                log.debug("[支付回写-入站] 非本域支付消息，跳过: businessType={}, businessId={}", message.businessType(),
                        message.businessId());
                return;
            }
            log.info("[支付回写-入站] 回写赔案赔付完成: claimId={}, paymentNo={}", message.businessId(),
                    message.paymentNo());
            paymentCompletionOrchestrator.completePayment(message.businessId(), message.paymentNo());
        } catch (Exception e) {
            // 聚合根前置校验（非 APPROVED+已结算）会抛异常，属业务性拒绝；消费失败交由 Kafka 重试/DLQ 兜底
            log.error("[支付回写-入站] 回写失败（由 Kafka 重试兜底）: payload={}", payload, e);
            throw e;
        }
    }

    /**
     * 监听支付出账未成功消息并标记赔案赔付失败。
     * <p>
     * 🔴 <b>必须按 {@code businessType} 过滤本域消息</b>（同成功路径）：本主题是支付域对**所有**业务域
     * 出款未成功的统一出口，不过滤则保费/退费业务的 {@code businessId}（保单ID/保全单ID）会被当作赔案ID。
     * </p>
     * <p>
     * 🔴 <b>与成功消息各自独立 {@code @KafkaListener}</b>：不写成 {@code topics={甲,乙}} 合并形式——
     * 目录守护测试只解析首个主题常量，合并会让第二个主题漏登记而拓扑比对失败；且分主题后两个消费位点
     * 互不阻塞，失败消息积压不影响成功回写。
     * </p>
     */
    @KafkaListener(topics = ClaimConstants.KafkaTopic.PAYMENT_ORDER_FAILED,
            groupId = "${spring.kafka.consumer.group-id}")
    public void onPaymentFailed(String payload) {
        log.debug("[支付回写-入站] 收到支付出账未成功消息: {}", payload);
        try {
            PaymentOrderFailedMessage message = JSONObject.parseObject(payload, PaymentOrderFailedMessage.class);
            if (message == null || message.businessId() == null || message.paymentNo() == null) {
                log.warn("[支付回写-入站] 未成功消息字段缺失，忽略: {}", payload);
                return;
            }
            if (!BusinessDomainType.CLAIM.getCode().equals(message.businessType())) {
                log.debug("[支付回写-入站] 非本域支付消息，跳过: businessType={}, businessId={}", message.businessType(),
                        message.businessId());
                return;
            }
            log.info("[支付回写-入站] 标记赔案赔付失败: claimId={}, paymentNo={}, resultType={}", message.businessId(),
                    message.paymentNo(), message.resultType());
            paymentCompletionOrchestrator.recordPaymentFailure(message.businessId(), message.paymentNo(),
                    message.resultType(), message.reason());
        } catch (Exception e) {
            // 聚合层已对「重复消息 / 跨主题乱序」做幂等静默，此处抛出的只应是基础设施故障（DB 不可达等），
            // 交由 Kafka 重试兜底是合理语义。
            log.error("[支付回写-入站] 失败回写异常（由 Kafka 重试兜底）: payload={}", payload, e);
            throw e;
        }
    }
}
