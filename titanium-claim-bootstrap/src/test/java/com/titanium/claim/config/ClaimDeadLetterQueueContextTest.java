package com.titanium.claim.config;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.titanium.claim.bootstrap.ClaimApplication;
import com.titanium.claim.infrastructure.event.KafkaEventPublisher;
import com.titanium.clause.api.ClauseApi;
import com.titanium.policy.api.InsuranceApi;
import com.titanium.policy.api.PolicyApi;
import com.titanium.policy.api.PolicyCashValueApi;
import com.titanium.policy.api.PolicyFieldCatalogApi;
import com.titanium.policy.api.PolicyIssuanceApi;
import com.titanium.policy.api.ProposalApi;

/**
 * 理赔域出站处理组的上下文级守护（启动真实组合根，非 yml 文本解析）
 * <p>
 * 与 {@code ClaimAxonProcessingGroupConfigurationTest}（读 yml 文本）分工：本类验证的是
 * <b>运行时装配结果</b>——yml 声明、{@code @ProcessingGroup} 取值、Axon 实际建出的处理器三者
 * 对得上，且死信队列真能取到。文本解析类测不出「配置写了但 Axon 没照做」的情形，二者互补。
 * </p>
 * <p>
 * 🔴 <b>守护的两个出站组</b>（m6-909 建、m6-913 补齐第二个）：
 * <ul>
 *   <li>{@code claim-kafka-group}（{@link KafkaEventPublisher}）——跨域事件外发；</li>
 *   <li>{@code claim-settlement-group}（{@code ClaimSettlementPaymentSaga}）——理赔赔付派发。</li>
 * </ul>
 * 两条链路的失败都只能靠「抛出 → 入 DLQ → {@code DeadLetterQueueService} 定时重投」兜底：
 * ① 组未注册（yml 键与 {@code @ProcessingGroup} 漂移）→ 处理器仍存在却退回默认配置，**不报错**；
 * ② 非 tracking（改回 subscribing）→ {@code SequencedDeadLetterQueue} 拿不到，失败即永久丢失；
 * ③ 未启用 DLQ（删 dlq 配置）→ {@code sequencedDeadLetterProcessor} 恒空，重投服务静默空转。
 * 三条都不会让构建失败，只能靠本类拦下。
 * </p>
 * <p>
 * ⚠️ tracking 的已知代价是「首启位点」：令牌不存在时从事件流<b>头部</b>开始消费。该风险由
 * application.yml 的 {@code titanium.axon.outbound-relay.groups} 登记消解（登记后首启取流末端），
 * 故改动本组配置时须同步检查该登记项，二者是一组（已由 yml 解析测试守护）。
 * </p>
 * <p>
 * <b>为何 mock 全部 Feign 客户端</b>：本地无 {@code spring-cloud-starter-loadbalancer}，
 * 未被 mock 的客户端会因惰性解析失败而拖垮上下文。**新增 Feign 契约时必须同步补入下发表**。
 * </p>
 */
@SpringBootTest(classes = ClaimApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:claim-dlq;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.liquibase.enabled=false",
                "spring.task.scheduling.enabled=false",
                "axon.axonserver.enabled=false"
        })
@MockitoBean(types = {
        ClauseApi.class,
        InsuranceApi.class,
        PolicyApi.class,
        PolicyCashValueApi.class,
        PolicyFieldCatalogApi.class,
        PolicyIssuanceApi.class,
        ProposalApi.class
})
class ClaimDeadLetterQueueContextTest {

    @Autowired
    private EventProcessingConfiguration eventProcessingConfiguration;

    @Test
    @DisplayName("跨域出站组 claim-kafka-group：已注册 + tracking + DLQ 可用")
    void providesTrackingKafkaPublisherGroupWithDeadLetterQueue() {
        assertOutboundGroupIsTrackingWithDeadLetterQueue(KafkaEventPublisher.PROCESSING_GROUP);
    }

    @Test
    @DisplayName("赔付派发组 claim-settlement-group：已注册 + tracking + DLQ 可用（m6-913 补齐）")
    void providesTrackingSettlementGroupWithDeadLetterQueue() {
        assertOutboundGroupIsTrackingWithDeadLetterQueue("claim-settlement-group");
    }

    /**
     * 断言某出站处理组按「tracking + DLQ」装配（三条断言各有分工，见类注释）。
     */
    private void assertOutboundGroupIsTrackingWithDeadLetterQueue(String processingGroup) {
        var processor = eventProcessingConfiguration.eventProcessorByProcessingGroup(processingGroup);
        assertTrue(processor.isPresent(),
                "未注册处理组 " + processingGroup
                        + "：@ProcessingGroup 与 application.yml 的 processors 键不一致");
        assertInstanceOf(TrackingEventProcessor.class, processor.get(),
                "处理组 " + processingGroup
                        + " 未按 tracking 模式装配：subscribing 拿不到死信队列，发布失败即永久丢失");

        assertTrue(eventProcessingConfiguration.sequencedDeadLetterProcessor(processingGroup).isPresent(),
                "处理组 " + processingGroup
                        + " 未启用死信队列：需在 application.yml 该组下配 dlq.enabled=true，"
                        + "否则 DeadLetterQueueService 静默空转、失败事件永不重投");
    }
}
