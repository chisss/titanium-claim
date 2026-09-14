package com.titanium.claim.infrastructure.adapter.payment;

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

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.axonframework.config.ProcessingGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.alibaba.fastjson2.JSONObject;

import com.titanium.claim.application.saga.ClaimSettlementPaymentSaga;
import com.titanium.claim.common.constant.ClaimConstants;
import com.titanium.claim.port.payment.PaymentServicePort.ClaimPayoutInstruction;
import com.titanium.claim.valueobject.BenefitCalculation;
import com.titanium.common.kafka.KafkaPublishException;

/**
 * 支付服务 Adapter 出站契约测试（理赔赔付派发链路）
 * <p>
 * 锁死 {@code payment-order-created} 的出站契约：① 主题名取 {@link ClaimConstants.KafkaTopic} 唯一事实来源；
 * ② 分区键为<b>理赔案件ID</b>（消费端建单单元是「赔案派生的支付单」，须同赔案保序，且与出站时的
 * {@code businessId} 一致）；③ 载荷字段清单与对端 payment 域入站防腐镜像
 * {@code ClaimPayoutInstructionMessage} 承接的字段逐字一致（改字段即红）；④ 返回值为入参 {@code claimId}
 * （不是分区偏移、也不是支付单号——支付单号由 payment 域按 {@code CP- + claimId} 确定性派生）。
 * </p>
 * <p>
 * 另锁死出站可靠性（m6-913）：⑤ 🔴 <b>发布失败必须抛出</b>——出站处理组 {@code claim-settlement-group}
 * 为 tracking + DLQ 形态，Axon 只对「处理器抛出的异常」入队死信；原实现「发后即弃 future」会让
 * Kafka 不可达/确认超时**静默丢失**赔付指令（赔案永不付款且无痕迹）；⑥ {@code send} 自身同步抛出的异常
 * （客户端缓冲区满、{@code max.block.ms} 到期）同样须被包装抛出，不能漏网；⑦ 出站组名与
 * {@code @ProcessingGroup} 一致，漂移会使 DLQ 配置与首启位点登记同时落空。
 * </p>
 */
class PaymentServiceAdapterTest {

    private static final String CLAIM_ID  = "CLAIM-001";
    private static final String TENANT_ID = "tenant-1";

    private KafkaTemplate<String, String> kafkaTemplate;

    private PaymentServiceAdapter          adapter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        // 默认发布成功：适配器现在会等待 broker 确认，未 stub 时 send 返回 null 会直接 NPE
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        adapter = new PaymentServiceAdapter(kafkaTemplate);
    }

    @Test
    @DisplayName("赔付指令：按案件ID分区发到 payment-order-created，载荷字段与对端入站镜像逐字一致")
    void shouldPublishPayoutInstructionWithClaimIdPartitionKey() {
        adapter.createClaimPayout(instruction(null, "BANK_TRANSFER", "ACC-001", null));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, times(1)).send(eq(ClaimConstants.KafkaTopic.PAYMENT_ORDER_CREATED), eq(CLAIM_ID),
                payloadCaptor.capture());

        JSONObject payload = JSONObject.parseObject(payloadCaptor.getValue());
        assertNotNull(payload);
        // 对端 payment 的入站镜像 ClaimPayoutInstructionMessage 承接的字段，逐字断言
        assertEquals(CLAIM_ID, payload.getString("claimId"), "赔案ID：出站以它为消息 key 与 businessId");
        assertEquals("POL-001", payload.getString("policyId"), "关联保单ID，入站记入备注不作业务单号");
        assertEquals(0, new BigDecimal("8888.00").compareTo(payload.getBigDecimal("amount")), "赔付金额");
        assertEquals("BANK_TRANSFER", payload.getString("payoutMethodCode"), "给付方式编码（中文名由消费端经共享枚举渲染）");
        assertEquals("ACC-001", payload.getString("payeeAccount"), "单一收款账户；分账给付时为空");
        assertEquals(TENANT_ID, payload.getString("tenantId"),
                "🔴 租户随载荷传递而非从消费线程上下文推断（入站为内部跨域通道，无 HTTP 租户上下文）");
    }

    @Test
    @DisplayName("身故分账给付：beneficiaryShares 逐字段外发，收款方留空由支付域分账")
    void shouldPublishBeneficiarySharesForDeathBenefit() {
        List<BenefitCalculation.BeneficiaryShare> shares = List.of(
                new BenefitCalculation.BeneficiaryShare("BEN-001", "张三", new BigDecimal("0.60"),
                        new BigDecimal("6000.00")),
                new BenefitCalculation.BeneficiaryShare("BEN-002", "李四", new BigDecimal("0.40"),
                        new BigDecimal("4000.00")));

        adapter.createClaimPayout(instruction(new BigDecimal("10000.00"), "BANK_TRANSFER", null, shares));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, times(1)).send(eq(ClaimConstants.KafkaTopic.PAYMENT_ORDER_CREATED), eq(CLAIM_ID),
                payloadCaptor.capture());

        JSONObject payload = JSONObject.parseObject(payloadCaptor.getValue());
        assertEquals(2, payload.getJSONArray("beneficiaryShares").size(), "分账明细逐笔外发");
        JSONObject first = payload.getJSONArray("beneficiaryShares").getJSONObject(0);
        assertEquals("BEN-001", first.getString("beneficiaryId"));
        assertEquals("张三", first.getString("beneficiaryName"));
        assertEquals(0, new BigDecimal("0.60").compareTo(first.getBigDecimal("benefitRatio")), "受益比例");
        assertEquals(0, new BigDecimal("6000.00").compareTo(first.getBigDecimal("amount")), "该受益人应得给付额");
    }

    @Test
    @DisplayName("返回值为入参 claimId（不是分区偏移、也不是支付单号）")
    void shouldReturnClaimIdAsDispatchIdentifier() {
        String dispatched = adapter.createClaimPayout(instruction(null, "BANK_TRANSFER", "ACC-001", null));

        assertEquals(CLAIM_ID, dispatched,
                "支付单号由 payment 域按 CP- + claimId 确定性派生，本端口不返回支付单号");
    }

    @Test
    @DisplayName("🔴 发布失败必须抛出（不再静默丢弃），否则事件不会进死信队列、无法重投")
    void shouldThrowWhenSendFails() {
        CompletableFuture<SendResult<String, String>> failed =
                CompletableFuture.failedFuture(new RuntimeException("broker unreachable"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failed);

        ClaimPayoutInstruction instruction = instruction(null, "BANK_TRANSFER", "ACC-001", null);

        KafkaPublishException exception = assertThrows(KafkaPublishException.class,
                () -> adapter.createClaimPayout(instruction));

        assertTrue(exception.getMessage().contains(ClaimConstants.KafkaTopic.PAYMENT_ORDER_CREATED),
                "异常须携带主题以便定位");
        assertNotNull(exception.getCause(), "须保留底层失败原因");
    }

    @Test
    @DisplayName("🔴 send 自身同步抛出（缓冲区满/max.block.ms 到期）也须包装抛出，不能漏网")
    void shouldThrowWhenSendThrowsSynchronously() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("buffer exhausted"));

        ClaimPayoutInstruction instruction = instruction(null, "BANK_TRANSFER", "ACC-001", null);

        assertThrows(KafkaPublishException.class, () -> adapter.createClaimPayout(instruction),
                "send 在 future 创建之前抛出的异常同样须纳入包装，否则该路径仍会静默丢失");
    }

    @Test
    @DisplayName("出站处理组名与 @ProcessingGroup 一致（漂移会使 DLQ 与首启位点配置同时落空）")
    void shouldDeclareProcessingGroupConsistently() {
        ProcessingGroup annotation = ClaimSettlementPaymentSaga.class.getAnnotation(ProcessingGroup.class);

        assertNotNull(annotation, "赔付派发编排必须声明 @ProcessingGroup");
        assertEquals("claim-settlement-group", annotation.value(),
                "组名须与 application.yml 的 axon.eventhandling.processors 及 "
                        + "titanium.axon.outbound-relay.groups 两处配置键一致");
    }

    /**
     * 构造赔付指令（缺省字段固定，仅变化本用例关心的维度）。
     */
    private ClaimPayoutInstruction instruction(BigDecimal amount, String payoutMethodCode, String payeeAccount,
            List<BenefitCalculation.BeneficiaryShare> shares) {
        return new ClaimPayoutInstruction(CLAIM_ID, "POL-001",
                amount == null ? new BigDecimal("8888.00") : amount,
                payoutMethodCode, payeeAccount, shares, TENANT_ID);
    }
}
