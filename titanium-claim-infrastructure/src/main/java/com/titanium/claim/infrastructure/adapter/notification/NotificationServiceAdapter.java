package com.titanium.claim.infrastructure.adapter.notification;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;

import com.titanium.claim.common.constant.ClaimConstants;
import com.titanium.claim.port.notification.NotificationServicePort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 通知服务 Adapter（对端域：notification）
 * <p>
 * 实现 {@link NotificationServicePort}：经 Kafka {@code claim-rejected} 主题发布拒赔通知消息，
 * 由 notification 域防腐消费后按模板渲染拒赔通知书发送。消息载荷（{@code RejectionNotice}）
 * 即出站契约，notification 域按本契约定义入站防腐 record。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationServiceAdapter implements NotificationServicePort {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Override
    public void sendRejectionNotice(RejectionNotice notice) {
        String payload = JSON.toJSONString(notice);
        log.info("[拒赔通知-出站] 发布拒赔通知书消息: claimId={}, reasonCode={}", notice.claimId(), notice.reasonCode());
        kafkaTemplate.send(ClaimConstants.KafkaTopic.CLAIM_REJECTED, notice.claimId(), payload);
    }
}
