package com.titanium.claim.infrastructure.event;

import org.axonframework.eventhandling.EventHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;

import com.titanium.claim.common.constant.ClaimConstants;
import com.titanium.claim.event.ClaimCreatedEvent;
import com.titanium.claim.event.ClaimRejectedEvent;
import com.titanium.claim.event.ClaimStatusChangedEvent;
import com.titanium.claim.event.ClaimUpdatedEvent;
import com.titanium.claim.event.DeathBenefitSettledEvent;
import com.titanium.claim.event.DisabilityBenefitSettledEvent;
import com.titanium.claim.port.notification.NotificationServicePort;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * claim 域 Kafka 出站适配器（infrastructure）
 * <p>
 * 订阅本域领域事件并发布到 Kafka，供其它微服务防腐消费。跨域「身故给付 → 保单终止」经
 * {@link DeathBenefitSettledEvent} 发布到 {@code claim-death-benefit-settled} 主题，
 * 由 policy 域防腐监听器据 policyId 派发保单终止命令（纯 Axon Saga 无法跨微服务监听别域事件）。
 * </p>
 */
@Slf4j
@Component
@AllArgsConstructor
public class KafkaEventPublisher {
    private final KafkaTemplate<String, String> kafkaTemplate;

    @EventHandler
    public void handle(ClaimCreatedEvent event) {
        String eventJson = JSON.toJSONString(event);
        kafkaTemplate.send(ClaimConstants.KafkaTopic.CLAIM_CREATED,
                           event.claimId().toString(), eventJson);
    }

    @EventHandler
    public void handle(ClaimUpdatedEvent event) {
        String eventJson = JSON.toJSONString(event);
        kafkaTemplate.send(ClaimConstants.KafkaTopic.CLAIM_UPDATED,
                           event.claimId().toString(), eventJson);
    }

    @EventHandler
    public void handle(ClaimStatusChangedEvent event) {
        String eventJson = JSON.toJSONString(event);
        kafkaTemplate.send(ClaimConstants.KafkaTopic.CLAIM_STATUS_CHANGED,
                           event.claimId().toString(), eventJson);
    }

    /**
     * 发布身故给付结算事件到 Kafka，供 policy 域防腐监听器据 policyId 终止保单（给付后保单责任终结）。
     */
    @EventHandler
    public void handle(DeathBenefitSettledEvent event) {
        String eventJson = JSON.toJSONString(event);
        log.info("[身故给付-出站] 发布身故给付结算事件: claimId={}, policyId={}", event.claimId(), event.policyId());
        kafkaTemplate.send(ClaimConstants.KafkaTopic.DEATH_BENEFIT_SETTLED,
                           event.policyId(), eventJson);
    }

    /**
     * 发布全残给付结算事件到 Kafka，供 policy 域防腐监听器据 policyId 终止保单（给付后保单责任终结，同身故）。
     */
    @EventHandler
    public void handle(DisabilityBenefitSettledEvent event) {
        String eventJson = JSON.toJSONString(event);
        log.info("[全残给付-出站] 发布全残给付结算事件: claimId={}, policyId={}", event.claimId(), event.policyId());
        kafkaTemplate.send(ClaimConstants.KafkaTopic.DISABILITY_BENEFIT_SETTLED,
                           event.policyId(), eventJson);
    }

    /**
     * 发布理赔拒赔事件到 Kafka（claim-rejected 主题），载荷为拒赔通知出站契约
     * {@link com.titanium.claim.port.notification.NotificationServicePort.RejectionNotice}，
     * 供 notification 域按该契约定义入站防腐 record 消费并渲染拒赔通知书。
     */
    @EventHandler
    public void handle(ClaimRejectedEvent event) {
        NotificationServicePort.RejectionNotice notice = new NotificationServicePort.RejectionNotice(
                event.claimId().value(), event.policyId(), event.customerId(),
                event.reason() == null ? null : event.reason().getCode(), event.comment());
        String noticeJson = JSON.toJSONString(notice);
        log.info("[拒赔通知-出站] 发布拒赔通知: claimId={}, reasonCode={}", event.claimId(), notice.reasonCode());
        kafkaTemplate.send(ClaimConstants.KafkaTopic.CLAIM_REJECTED, event.claimId().value(), noticeJson);
    }
}
