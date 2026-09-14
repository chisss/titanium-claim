package com.titanium.claim.infrastructure.adapter.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.alibaba.fastjson2.JSONObject;

import com.titanium.claim.common.constant.ClaimConstants;
import com.titanium.claim.port.document.DocumentServicePort.ClaimDocument;
import com.titanium.common.kafka.KafkaPublishException;
import com.titanium.metadata.topic.CrossDomainTopics;

/**
 * 文档服务 Adapter 出站契约测试（理赔结案单证派发链路）
 * <p>
 * 锁死 {@code claim-closed} 的出站契约：① 主题名取自 {@link ClaimConstants.KafkaTopic}（其值又收敛自
 * {@link CrossDomainTopics} 唯一事实来源，非裸字符串）；② 分区键为<b>理赔案件ID</b>（消费端建档单元是
 * 「赔案派生的单证」，须同赔案保序）；③ 载荷字段清单与对端 document 域入站防腐镜像
 * {@code ClaimClosedMessage} 承接的字段逐字一致（改字段即红）；④ 返回值为入参 {@code claimId}
 * （不是分区偏移、也不是单证ID——单证ID由 document 域生成）。
 * </p>
 * <p>
 * 另锁死出站可靠性：⑤ 🔴 <b>发布失败必须抛出</b>——出站处理组 {@code claim-settlement-group} 为
 * tracking + DLQ 形态，Axon 只对「处理器抛出的异常」入队死信；「发后即弃 future」会让 Kafka 不可达时
 * 结案单证**静默不生成**且无痕迹；⑥ {@code send} 自身同步抛出的异常同样须被包装抛出，不能漏网。
 * </p>
 */
class DocumentServiceAdapterTest {

    private static final String          CLAIM_ID  = "CLAIM-001";
    private static final String          TENANT_ID = "tenant-1";
    private static final LocalDateTime   CLOSED_AT = LocalDateTime.of(2026, 9, 14, 10, 0);

    private KafkaTemplate<String, String> kafkaTemplate;

    private DocumentServiceAdapter        adapter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        // 默认发布成功：适配器等待 broker 确认，未 stub 时 send 返回 null 会直接 NPE
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        adapter = new DocumentServiceAdapter(kafkaTemplate);
    }

    @Test
    @DisplayName("结案单证：按赔案ID分区发到 claim-closed，载荷字段与对端入站镜像逐字一致")
    void shouldPublishClaimClosedWithClaimIdPartitionKey() {
        adapter.archiveClaimDocument(new ClaimDocument(CLAIM_ID, "POL-001", new BigDecimal("8888.00"),
                "BANK_TRANSFER", "核赔通过", CLOSED_AT, TENANT_ID));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, times(1)).send(eq(ClaimConstants.KafkaTopic.CLAIM_CLOSED), eq(CLAIM_ID),
                payloadCaptor.capture());

        JSONObject payload = JSONObject.parseObject(payloadCaptor.getValue());
        assertNotNull(payload);
        // 对端 document 的入站镜像 ClaimClosedMessage 承接的字段，逐字断言
        assertEquals(CLAIM_ID, payload.getString("claimId"), "赔案ID：出站以它为消息 key 与单证 businessId");
        assertEquals("POL-001", payload.getString("policyId"), "关联保单ID，渲染进单证正文");
        assertEquals(0, new BigDecimal("8888.00").compareTo(payload.getBigDecimal("settledAmount")), "赔付金额");
        assertEquals("BANK_TRANSFER", payload.getString("payoutMethodCode"),
                "给付方式编码（该枚举属理赔域，单证域按码原样渲染）");
        assertEquals("核赔通过", payload.getString("conclusion"), "结案说明");
        // 🔴 时间不硬编码线格式：fastjson2 本项目产出的 LocalDateTime 形态为空格分隔的
        //    yyyy-MM-dd HH:mm:ss（实测），按类型回读可同时守护「可往返」与「格式漂移」两件事
        assertEquals(CLOSED_AT, payload.getObject("closedAt", LocalDateTime.class), "结案时间");
        assertEquals(TENANT_ID, payload.getString("tenantId"),
                "🔴 租户随载荷传递而非从消费线程上下文推断（入站为内部跨域通道，无 HTTP 租户上下文）");
    }

    @Test
    @DisplayName("拒赔结案：无赔付事实，金额与给付方式以 null 外发（不臆造金额）")
    void shouldPublishNullPayoutElementsForRejection() {
        adapter.archiveClaimDocument(new ClaimDocument(CLAIM_ID, "POL-001", null, null, "NOT_IN_COVERAGE/超范围",
                CLOSED_AT, TENANT_ID));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(ClaimConstants.KafkaTopic.CLAIM_CLOSED), eq(CLAIM_ID), payloadCaptor.capture());

        JSONObject payload = JSONObject.parseObject(payloadCaptor.getValue());
        assertNull(payload.getBigDecimal("settledAmount"));
        assertNull(payload.getString("payoutMethodCode"));
        assertEquals("NOT_IN_COVERAGE/超范围", payload.getString("conclusion"));
    }

    @Test
    @DisplayName("返回值为入参 claimId（不是分区偏移、也不是单证ID）")
    void shouldReturnClaimIdAsDispatchIdentifier() {
        String dispatched = adapter.archiveClaimDocument(new ClaimDocument(CLAIM_ID, "POL-001", null, null, null,
                CLOSED_AT, TENANT_ID));

        assertEquals(CLAIM_ID, dispatched, "单证ID由 document 域生成，本端口不返回单证ID");
    }

    @Test
    @DisplayName("🔴 发布失败必须抛出（不再静默丢弃），否则事件不会进死信队列、无法重投")
    void shouldThrowWhenSendFails() {
        CompletableFuture<SendResult<String, String>> failed =
                CompletableFuture.failedFuture(new RuntimeException("broker unreachable"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failed);

        ClaimDocument document = new ClaimDocument(CLAIM_ID, "POL-001", null, null, null, CLOSED_AT, TENANT_ID);

        KafkaPublishException exception = assertThrows(KafkaPublishException.class,
                () -> adapter.archiveClaimDocument(document));

        assertTrue(exception.getMessage().contains(ClaimConstants.KafkaTopic.CLAIM_CLOSED), "异常须携带主题以便定位");
        assertNotNull(exception.getCause(), "须保留底层失败原因");
    }

    @Test
    @DisplayName("🔴 send 自身同步抛出（缓冲区满/max.block.ms 到期）也须包装抛出，不能漏网")
    void shouldThrowWhenSendThrowsSynchronously() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("buffer exhausted"));

        ClaimDocument document = new ClaimDocument(CLAIM_ID, "POL-001", null, null, null, CLOSED_AT, TENANT_ID);

        assertThrows(KafkaPublishException.class, () -> adapter.archiveClaimDocument(document),
                "send 在 future 创建之前抛出的异常同样须纳入包装，否则该路径仍会静默丢失");
    }
}
