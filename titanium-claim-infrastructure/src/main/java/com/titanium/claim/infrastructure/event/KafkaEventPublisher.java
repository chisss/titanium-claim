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
     * 发布理赔拒赔事件到 Kafka（{@code claim-rejected} 主题），供 notification 域防腐消费后按模板
     * 渲染拒赔通知书投递（唯一消费方，2026-09-14 m6-905 接线）。
     * <p>
     * 载荷即 {@link ClaimRejectedEvent} 序列化 JSON（与本域其余五个主题同形态）：通知域所需的拒赔原因码
     * （{@code reason}）、拒赔时间（{@code rejectedAt}）、客户标识（{@code customerId}）与租户均在其中，
     * 无需另设扁平通知契约。🔴 分区键取 <b>claimId</b>：消费端写单元是「理赔案件派生的通知记录」，
     * 与案件维度同构，案件内保序（同一案件至多一次拒赔——已拒赔的重复指令在聚合侧被幂等忽略）。
     * </p>
     * <p>
     * 🔴 本方法曾是<b>同主题双发送点</b>之一（另一处为已删除的死代码
     * {@code NotificationServiceAdapter}，其 {@code sendRejectionNotice} 全仓零调用方）；死代码一并清除，
     * 保留本处作为唯一出站点。
     * </p>
     */
    @EventHandler
    public void handle(ClaimRejectedEvent event) {
        String eventJson = JSON.toJSONString(event);
        log.info("[拒赔通知-出站] 发布拒赔事件: claimId={}, reason={}",
                 event.claimId(), event.reason() == null ? null : event.reason().getCode());
        kafkaTemplate.send(ClaimConstants.KafkaTopic.CLAIM_REJECTED, event.claimId().value(), eventJson);
    }
}
