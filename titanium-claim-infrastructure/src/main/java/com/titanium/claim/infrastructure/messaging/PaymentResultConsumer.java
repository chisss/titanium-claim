package com.titanium.claim.infrastructure.messaging;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSONObject;

import com.titanium.claim.application.orchestration.payment.PaymentCompletionOrchestrator;
import com.titanium.claim.common.constant.ClaimConstants;
import com.titanium.claim.infrastructure.messaging.inbound.PaymentOrderFailedMessage;
import com.titanium.claim.infrastructure.messaging.inbound.PaymentOrderPaidMessage;
import com.titanium.common.context.RequestContextHolder;
import com.titanium.common.messaging.KafkaIdempotencyGuard;
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
 * <p>
 * 🔴 <b>身份播种（消费侧无请求上下文）</b>：Kafka 监听线程没有 Web 请求上下文，两个入口在校验通过后
 * 一律先按载荷 {@code tenantId} 建立身份作用域（{@link RequestContextHolder#runAsTenant}）再委托编排器——
 * 身份必须在<b>派发命令之前</b>建立，下游 Axon 事件线程才能继承，出站 Feign（{@code /api/v1/**}
 * 强制准入）才不会因缺租户头被 400 拦下；作用域退出即还原，不污染线程池复用线程。
 * 载荷缺租户属**数据缺陷**（对端出站载荷自该字段引入起即携带），重投永远不会成功，
 * 故记 WARN 跳过且**不重抛**——把重投通道留给可恢复故障。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentResultConsumer {

    /** 消费者组标识（幂等键组成部分，隔离不同消费语义） */
    private static final String CONSUMER_GROUP = "claim-payment-result";

    private final PaymentCompletionOrchestrator paymentCompletionOrchestrator;
    private final KafkaIdempotencyGuard         idempotencyGuard;

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
        String claimId = null;
        String paymentNo = null;
        String tenantId = null;
        try {
            PaymentOrderPaidMessage message = JSONObject.parseObject(payload, PaymentOrderPaidMessage.class);
            if (message == null || message.businessId() == null || message.paymentNo() == null
                    || message.tenantId() == null || message.tenantId().isBlank()) {
                log.warn("[支付回写-入站] 消息字段缺失（含租户），忽略: {}", payload);
                return;
            }
            if (!BusinessDomainType.CLAIM.getCode().equals(message.businessType())) {
                log.debug("[支付回写-入站] 非本域支付消息，跳过: businessType={}, businessId={}", message.businessType(),
                        message.businessId());
                return;
            }
            log.info("[支付回写-入站] 回写赔案赔付完成: claimId={}, paymentNo={}", message.businessId(),
                    message.paymentNo());
            // 🔴 m22-07（MSG-02）幂等：Kafka 为 at-least-once，重复投递会让赔付状态重复推进。
            // 用 Redis SETNX 原子占位（读模型投影最终一致，先查表存在竞态窗口 —— billing 的既有判定）。
            // 🔴 身份段必须含 paymentNo，不可只用 claimId：本消息代表**某个支付单**的出账结果，而非
            // 「某赔案的支付结果」。出账失败后人工重派会生成新 paymentNo（m21-13 的赔付可重派），
            // 只用 claimId 会把重派成功后的回写误判为重复投递而永久跳过，赔案卡在 APPROVED 再无消息可等。
            claimId = message.businessId();
            paymentNo = message.paymentNo();
            tenantId = message.tenantId();
            if (!idempotencyGuard.tryAcquire(CONSUMER_GROUP, tenantId, claimId, paymentNo)) {
                return;
            }
            // 本方法运行在 Kafka 监听线程（无 Web 请求上下文）：先按消息载荷建立身份作用域，使回写链路派发的
            // 命令及其下游 Axon 事件线程继承该身份（出站 Feign 契约在 /api/v1/** 强制准入，缺租户头即 400）；
            // 作用域退出即还原，不污染线程池复用线程。
            RequestContextHolder.runAsTenant(message.tenantId(),
                    () -> paymentCompletionOrchestrator.completePayment(message.businessId(), message.paymentNo()));
        } catch (Exception e) {
            // 聚合根前置校验（非 APPROVED+已结算）会抛异常，属业务性拒绝；消费失败交由 Kafka 重试/DLQ 兜底
            // 🔴 必须释放幂等占位：否则重投递被判为「重复投递」而永久跳过，
            //    把「可重试的失败」退化为「永久丢失」（引入幂等时最常见的自伤）
            if (claimId != null && paymentNo != null && tenantId != null) {
                idempotencyGuard.release(CONSUMER_GROUP, tenantId, claimId, paymentNo);
            }
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
     * <p>
     * 🔴 <b>本条有意不挂 {@link KafkaIdempotencyGuard}（与成功路径的不对称是刻意的）</b>：重复/乱序的
     * 失败消息在聚合层已被静默吸收（{@code RecordPaymentFailureCommand} 仅当「已结算赔付中」才落事件，
     * 其余 return 不抛异常），既不产生重复副作用也不进 DLT——再套一层 Redis 幂等只增加一个「占位未释放
     * ⇒ 重投递被永久跳过」的自伤面，无收益。**成功路径则相反**：重复消息会被聚合前置校验抛异常
     * （已 PAID 时 {@code status != APPROVED}），重投递会一路重试到 DLT，故必须就地短路。
     * </p>
     */
    @KafkaListener(topics = ClaimConstants.KafkaTopic.PAYMENT_ORDER_FAILED,
            groupId = "${spring.kafka.consumer.group-id}")
    public void onPaymentFailed(String payload) {
        log.debug("[支付回写-入站] 收到支付出账未成功消息: {}", payload);
        try {
            PaymentOrderFailedMessage message = JSONObject.parseObject(payload, PaymentOrderFailedMessage.class);
            if (message == null || message.businessId() == null || message.paymentNo() == null
                    || message.tenantId() == null || message.tenantId().isBlank()) {
                log.warn("[支付回写-入站] 未成功消息字段缺失（含租户），忽略: {}", payload);
                return;
            }
            if (!BusinessDomainType.CLAIM.getCode().equals(message.businessType())) {
                log.debug("[支付回写-入站] 非本域支付消息，跳过: businessType={}, businessId={}", message.businessType(),
                        message.businessId());
                return;
            }
            log.info("[支付回写-入站] 标记赔案赔付失败: claimId={}, paymentNo={}, resultType={}", message.businessId(),
                    message.paymentNo(), message.resultType());
            // 与成功路径同源：本方法同样运行在 Kafka 监听线程，先按载荷建立身份作用域再派发命令。
            RequestContextHolder.runAsTenant(message.tenantId(),
                    () -> paymentCompletionOrchestrator.recordPaymentFailure(message.businessId(),
                            message.paymentNo(), message.resultType(), message.reason()));
        } catch (Exception e) {
            // 聚合层已对「重复消息 / 跨主题乱序」做幂等静默，此处抛出的只应是基础设施故障（DB 不可达等），
            // 交由 Kafka 重试兜底是合理语义。
            log.error("[支付回写-入站] 失败回写异常（由 Kafka 重试兜底）: payload={}", payload, e);
            throw e;
        }
    }
}
