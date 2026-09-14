package com.titanium.claim.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.axonframework.config.ProcessingGroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import com.titanium.claim.application.saga.ClaimSettlementPaymentSaga;
import com.titanium.claim.infrastructure.event.KafkaEventPublisher;

/**
 * 出站处理组的 yml 配置一致性守护（不启 Spring 上下文，直接解析 application.yml）
 * <p>
 * 🔴 守护的是一次真实缺陷（m6-913）：{@code ClaimSettlementPaymentSaga} 声明了
 * {@code @ProcessingGroup("claim-settlement-group")}，但 yml 的 {@code axon.eventhandling.processors}
 * 中**没有该键**——Axon 对未声明的组按默认规则退化，结果是①处理组形同虚设、②无 DLQ、
 * ③首启位点从事件流头部开始。三条后果**都不报错**，只能靠本类这种显式断言发现。
 * </p>
 * <p>
 * 断言两处配置键与 {@code @ProcessingGroup} 取值三者对齐，任一漂移即红：
 * ① {@code axon.eventhandling.processors.{group}}（模式与 DLQ 开关）；
 * ② {@code titanium.axon.outbound-relay.groups}（首启位点取事件流末端）。
 * </p>
 */
class ClaimAxonProcessingGroupConfigurationTest {

    @Test
    @DisplayName("赔付派发组：yml 声明 tracking + DLQ + batchSize（缺键即退化，且无重投）")
    void configuresDeadLetterQueueForSettlementProcessingGroup() throws IOException {
        String processingGroup = ClaimSettlementPaymentSaga.class.getAnnotation(ProcessingGroup.class).value();
        List<PropertySource<?>> sources = load();

        assertEquals("claim-settlement-group", processingGroup, "组名须与 yml 配置键逐字一致");
        assertEquals("tracking", property(sources, "axon.eventhandling.processors." + processingGroup + ".mode"),
                "🔴 DLQ 仅支持流式处理器：SubscribingEventProcessor 拿不到 SequencedDeadLetterQueue");
        assertEquals(Boolean.TRUE,
                property(sources, "axon.eventhandling.processors." + processingGroup + ".dlq.enabled"));
        assertEquals(1, property(sources, "axon.eventhandling.processors." + processingGroup + ".batchSize"),
                "batchSize=1：单条确认，避免批量失败时整批入队");
    }

    @Test
    @DisplayName("出站组均登记为事件流末端位点（漏登会把全量历史事件重放给下游）")
    void registersBothOutboundGroupsAsRelayGroups() throws IOException {
        List<String> relayGroups = indexedValues(load(), "titanium.axon.outbound-relay.groups");

        String settlementGroup = ClaimSettlementPaymentSaga.class.getAnnotation(ProcessingGroup.class).value();
        assertTrue(relayGroups.contains(settlementGroup),
                "赔付派发组须登记为出站组，否则首启位点从事件流头部开始：" + relayGroups);
        assertTrue(relayGroups.contains(KafkaEventPublisher.PROCESSING_GROUP),
                "跨域出站组须登记为出站组：" + relayGroups);
    }

    private List<PropertySource<?>> load() throws IOException {
        return new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
    }

    /**
     * 取首个非空属性值（跨多个 yml 文档/源查找）。
     */
    private Object property(List<PropertySource<?>> sources, String key) {
        return sources.stream()
                .map(source -> source.getProperty(key))
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
    }

    /**
     * 取 YAML 列表的展开形态（{@code key[0]}、{@code key[1]}…）。
     */
    private List<String> indexedValues(List<PropertySource<?>> sources, String key) {
        List<String> values = new ArrayList<>();
        for (int i = 0; ; i++) {
            Object value = property(sources, key + "[" + i + "]");
            if (value == null) {
                return values;
            }
            values.add(value.toString());
        }
    }
}
