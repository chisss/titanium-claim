package com.titanium.claim.application.saga;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;

import com.titanium.claim.event.ClaimSettledEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * 理赔赔付集成编排（核赔结算 → 支付域 CLAIM_PAYOUT 触发点）
 * <p>
 * 监听 {@link ClaimSettledEvent}，作为理赔域向支付域发起赔付的集成缝。 目标行为：构造支付域
 * {@code CreatePaymentOrderCommand(paymentType=CLAIM_PAYOUT, businessId=claimId, businessType=CLAIM)}
 * 并经 CommandGateway/Feign 派发到 titanium-payment。
 * </p>
 * <p>
 * <b>当前阶段</b>：跨服务异步事件总线（注册中心 + Kafka/AxonServer）尚未打通（见《领域设计指导文档》§〇
 * P0 基础设施）。本编排先落地集成缝与赔付数据提取，跨服务派发待事件总线就绪后接入，避免在缺基础设施时
 * 引入不可验证的同步耦合。
 * </p>
 */
@Slf4j
@Component
@ProcessingGroup("claim-settlement-group")
public class ClaimSettlementPaymentSaga {

    /**
     * 核赔结算完成 → 触发理赔赔付支付单
     */
    @EventHandler
    public void on(ClaimSettledEvent event) {
        log.info("[理赔赔付] 核赔结算完成, claimId={}, 核定赔付金额={}, 给付方式={} —— 待支付域 CLAIM_PAYOUT 派发",
                event.claimId(), event.settlement().getSettledAmount(), event.settlement().getPayoutMethod());
        // TODO 事件总线就绪后：commandGateway/paymentClient 派发
        //   CreatePaymentOrderCommand(paymentType=CLAIM_PAYOUT, businessId=event.claimId().value(),
        //       businessType="CLAIM", amount=event.settlement().getSettledAmount(), ...)
    }
}
