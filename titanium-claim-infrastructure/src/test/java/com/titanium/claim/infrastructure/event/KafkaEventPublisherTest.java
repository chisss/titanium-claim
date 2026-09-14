package com.titanium.claim.infrastructure.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import org.axonframework.config.ProcessingGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.alibaba.fastjson2.JSONObject;

import com.titanium.claim.event.ClaimRejectedEvent;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.common.kafka.KafkaPublishException;
import com.titanium.metadata.enums.claim.RejectReason;
import com.titanium.metadata.topic.CrossDomainTopics;

/**
 * 理赔域 Kafka 出站发布器测试（拒赔链路）
 * <p>
 * 锁死 {@code claim-rejected} 的出站契约（2026-09-14 m6-905 接线后唯一出站点）：
 * ① 主题名取 {@link CrossDomainTopics} 唯一事实来源；② 分区键为<b>理赔案件ID</b>（消费端写单元是
 * 「案件派生的通知记录」，须同案件保序）；③ 载荷字段清单与对端入站防腐镜像
 * {@code ClaimRejectedMessage} 承接的字段逐字一致（改字段即红）；④ <b>同一事件只发一次</b>——
 * 防回归：该主题曾并存两个发送点（本处 + 已删除的死代码 {@code NotificationServiceAdapter}）。
 * </p>
 * <p>
 * 另锁死出站可靠性两件事（m6-909）：⑤ 🔴 <b>发布失败必须抛出</b>——出站处理组为 tracking + DLQ 形态，
 * Axon 只对「处理器抛出的异常」入队死信；失败仅记日志则事件既不重投也不留痕，等于静默丢失；
 * ⑥ 出站组名与 {@code @ProcessingGroup} 一致，漂移会使 DLQ 配置落空。
 * </p>
 */
class KafkaEventPublisherTest {

    private static final String CLAIM_ID  = "CLAIM-001";
    private static final String TENANT_ID = "tenant-1";

    private KafkaTemplate<String, String> kafkaTemplate;

    private KafkaEventPublisher          publisher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        // 默认发布成功：发布器现在会等待 broker 确认，未 stub 时 send 返回 null 会直接 NPE
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        publisher = new KafkaEventPublisher(kafkaTemplate);
    }

    @Test
    @DisplayName("拒赔事件：按案件ID分区发到 claim-rejected，载荷含原因码/客户/拒赔时间/租户")
    void shouldPublishClaimRejectedWithClaimIdPartitionKey() {
        publisher.handle(new ClaimRejectedEvent(ClaimId.of(CLAIM_ID), "POL-001", "CUST-001",
                RejectReason.NOT_IN_COVERAGE, "内部核赔备注", LocalDateTime.of(2026, 9, 14, 10, 30), TENANT_ID));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, times(1)).send(eq(CrossDomainTopics.CLAIM_REJECTED), eq(CLAIM_ID),
                payloadCaptor.capture());

        JSONObject payload = JSONObject.parseObject(payloadCaptor.getValue());
        assertNotNull(payload);
        // 对端 notification 的入站镜像 ClaimRejectedMessage 承接的字段，逐字断言
        assertEquals(CLAIM_ID, payload.getJSONObject("claimId").getString("value"), "claimId 以值对象形态承接");
        assertEquals("POL-001", payload.getString("policyId"));
        assertEquals("CUST-001", payload.getString("customerId"), "收件人来源：拒赔通知书投递进该客户收件箱");
        assertEquals("NOT_IN_COVERAGE", payload.getString("reason"), "原因码（中文名由消费端经共享枚举渲染）");
        // 🔴 时间字段线格式以**实测**为准：fastjson2 把 LocalDateTime 序列化为空格分隔的
        // "yyyy-MM-dd HH:mm:ss"（非 ISO 的 'T' 分隔）。消费端 notification 同样用 fastjson2 反序列化，
        // 两端一致；此处锁死线格式，改动即红（教训同目录 §六.8 billing 的时间戳数组坑）。
        assertEquals("2026-09-14 10:30:00", payload.getString("rejectedAt"), "拒赔时间进通知书正文");
        assertEquals(TENANT_ID, payload.getString("tenantId"));
    }

    @Test
    @DisplayName("拒赔原因为空：载荷 reason 为 null，不抛异常")
    void shouldTolerateNullReason() {
        publisher.handle(new ClaimRejectedEvent(ClaimId.of(CLAIM_ID), "POL-001", "CUST-001", null, null,
                LocalDateTime.of(2026, 9, 14, 10, 30), TENANT_ID));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, times(1)).send(eq(CrossDomainTopics.CLAIM_REJECTED), eq(CLAIM_ID),
                payloadCaptor.capture());

        JSONObject payload = JSONObject.parseObject(payloadCaptor.getValue());
        assertEquals(null, payload.getString("reason"), "原因为空时消费端按「其他原因」兜底渲染");
    }

    @Test
    @DisplayName("🔴 发布失败必须抛出（不再静默丢弃），否则事件不会进死信队列、无法重投")
    void shouldThrowWhenSendFails() {
        CompletableFuture<SendResult<String, String>> failed =
                CompletableFuture.failedFuture(new RuntimeException("broker unreachable"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failed);

        ClaimRejectedEvent event = new ClaimRejectedEvent(ClaimId.of(CLAIM_ID), "POL-001", "CUST-001",
                RejectReason.NOT_IN_COVERAGE, null, LocalDateTime.of(2026, 9, 14, 10, 30), TENANT_ID);

        KafkaPublishException exception = assertThrows(KafkaPublishException.class, () -> publisher.handle(event));

        assertTrue(exception.getMessage().contains(CrossDomainTopics.CLAIM_REJECTED), "异常须携带主题以便定位");
        assertNotNull(exception.getCause(), "须保留底层失败原因");
    }

    @Test
    @DisplayName("出站处理组名与 @ProcessingGroup 一致（漂移会使 DLQ 与首启位点配置同时落空）")
    void shouldDeclareProcessingGroupConsistently() {
        ProcessingGroup annotation = KafkaEventPublisher.class.getAnnotation(ProcessingGroup.class);

        assertNotNull(annotation, "出站发布器必须声明 @ProcessingGroup");
        assertEquals(KafkaEventPublisher.PROCESSING_GROUP, annotation.value());
        assertEquals("claim-kafka-group", KafkaEventPublisher.PROCESSING_GROUP,
                "组名须与 application.yml 的 axon.eventhandling.processors 键一致");
    }
}
