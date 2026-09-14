package com.titanium.claim.infrastructure.event;

import org.axonframework.config.ProcessingGroup;
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
import com.titanium.common.kafka.KafkaPublishSupport;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * claim 域 Kafka 出站适配器（infrastructure）
 * <p>
 * 订阅本域领域事件并发布到 Kafka，供其它微服务防腐消费。跨域「身故给付 → 保单终止」经
 * {@link DeathBenefitSettledEvent} 发布到 {@code claim-death-benefit-settled} 主题，
 * 由 policy 域防腐监听器据 policyId 派发保单终止命令（纯 Axon Saga 无法跨微服务监听别域事件）。
 * </p>
 * <p>
 * <b>处理组形态</b>：{@link #PROCESSING_GROUP} 以 {@code mode: tracking} + {@code dlq.enabled: true}
 * 装配（见 application.yml）。tracking 是死信队列的前提；DLQ 让发布失败的事件由
 * {@code DeadLetterQueueService} 定时重投而非静默丢失。
 * </p>
 */
@Slf4j
@Component
@AllArgsConstructor
@ProcessingGroup(KafkaEventPublisher.PROCESSING_GROUP)
public class KafkaEventPublisher {

    /**
     * 跨域出站处理组名。
     * <p>🔴 必须与 application.yml 的 {@code axon.eventhandling.processors.<name>} 键一致：二者漂移时
     * Axon 会为本组退回默认处理器且丢失 DLQ 配置，处理器仍存在（故不报错）但失去重投能力。</p>
     */
    public static final String PROCESSING_GROUP = "claim-kafka-group";

    private final KafkaTemplate<String, String> kafkaTemplate;

    @EventHandler
    public void handle(ClaimCreatedEvent event) {
        publish(ClaimConstants.KafkaTopic.CLAIM_CREATED, event.claimId().toString(), event);
    }

    @EventHandler
    public void handle(ClaimUpdatedEvent event) {
        publish(ClaimConstants.KafkaTopic.CLAIM_UPDATED, event.claimId().toString(), event);
    }

    @EventHandler
    public void handle(ClaimStatusChangedEvent event) {
        publish(ClaimConstants.KafkaTopic.CLAIM_STATUS_CHANGED, event.claimId().toString(), event);
    }

    /**
     * 发布身故给付结算事件到 Kafka，供 policy 域防腐监听器据 policyId 终止保单（给付后保单责任终结）。
     * <p>🔴 分区键取 <b>policyId</b>（非 claimId）：消费端写单元是「保单」，须同保单保序。</p>
     */
    @EventHandler
    public void handle(DeathBenefitSettledEvent event) {
        log.info("[身故给付-出站] 发布身故给付结算事件: claimId={}, policyId={}", event.claimId(), event.policyId());
        publish(ClaimConstants.KafkaTopic.DEATH_BENEFIT_SETTLED, event.policyId(), event);
    }

    /**
     * 发布全残给付结算事件到 Kafka，供 policy 域防腐监听器据 policyId 终止保单（给付后保单责任终结，同身故）。
     * <p>🔴 分区键取 <b>policyId</b>（非 claimId）：消费端写单元是「保单」，须同保单保序。</p>
     */
    @EventHandler
    public void handle(DisabilityBenefitSettledEvent event) {
        log.info("[全残给付-出站] 发布全残给付结算事件: claimId={}, policyId={}", event.claimId(), event.policyId());
        publish(ClaimConstants.KafkaTopic.DISABILITY_BENEFIT_SETTLED, event.policyId(), event);
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
        log.info("[拒赔通知-出站] 发布拒赔事件: claimId={}, reason={}",
                 event.claimId(), event.reason() == null ? null : event.reason().getCode());
        publish(ClaimConstants.KafkaTopic.CLAIM_REJECTED, event.claimId().value(), event);
    }

    /**
     * 序列化并发布到 Kafka，等待 broker 确认。
     * <p>
     * 🔴 <b>失败会抛出</b>（broker 不可达、确认超时、主题无权限）→ 抛 {@code KafkaPublishException}，
     * 这是「失败可见 → 入 DLQ → 定时重投」链路的触发点。原先发后即弃 future 的写法会让失败静默丢失：
     * 既不重试、也不留痕、也无从对账。
     * </p>
     * <p>
     * 🔴 载荷是 fastjson2 产出的 JSON **字符串**，由 {@code StringSerializer} 逐字节透传；不得改为直接
     * 发事件 POJO——那会改变线上时间字段线格式（详见 {@code KafkaEventPublisherTest} 的逐字断言）。
     * </p>
     */
    private void publish(String topic, String key, Object payload) {
        String eventJson = JSON.toJSONString(payload);
        KafkaPublishSupport.awaitSent(() -> kafkaTemplate.send(topic, key, eventJson), topic, key);
    }
}
