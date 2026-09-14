package com.titanium.claim.infrastructure.adapter.document;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;

import com.titanium.claim.common.constant.ClaimConstants;
import com.titanium.claim.port.document.DocumentServicePort;
import com.titanium.common.kafka.KafkaPublishSupport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 文档服务 Adapter（对端域：document）
 * <p>
 * 实现 {@link DocumentServicePort}：经 Kafka {@code claim-closed} 主题外发理赔结案事实，由 document 域
 * 防腐消费后渲染正文、落盘并经 {@code GenerateDocumentCommand} 归档为 {@code CLAIM_DOC} 单证。
 * </p>
 * <p>
 * 🔴 <b>为何是 Kafka 而非 Feign</b>：document 域已有完整的「渲染 → 落盘 → 建档 → 登记生成完成」入站范式
 * （{@code PolicyIssuedDocumentEventListener} → {@code PolicyIssuedDocumentOrchestrator}），渲染与落盘职责
 * 整体在该域；走 Feign 会把这两件事倒灌回 claim 域，且把单证可用性绑进结案主流程。异步外发与同域
 * {@link com.titanium.claim.infrastructure.adapter.payment.PaymentServiceAdapter} 同构。
 * </p>
 * <p>
 * 🔴 <b>出站可靠性</b>：发送经 {@link KafkaPublishSupport#awaitSent} 同步等待 broker 确认，失败抛
 * {@code KafkaPublishException} → 调用方 {@code ClaimClosureDocumentSaga} 所在处理组
 * {@code claim-settlement-group}（tracking + DLQ）据此入队死信、由 {@code DeadLetterQueueService} 定时重投。
 * 若「发后即弃 future」，Kafka 不可达时结案单证会**静默不生成**且无任何痕迹。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentServiceAdapter implements DocumentServicePort {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Override
    public String archiveClaimDocument(ClaimDocument document) {
        String payload = JSON.toJSONString(document);
        log.info("[理赔结案单证-出站] 发布结案单证要素: claimId={}, 赔付金额={}, 结案说明={}", document.claimId(),
                document.settledAmount(), document.conclusion());
        KafkaPublishSupport.awaitSent(
                () -> kafkaTemplate.send(ClaimConstants.KafkaTopic.CLAIM_CLOSED, document.claimId(), payload),
                ClaimConstants.KafkaTopic.CLAIM_CLOSED, document.claimId());
        return document.claimId();
    }
}
