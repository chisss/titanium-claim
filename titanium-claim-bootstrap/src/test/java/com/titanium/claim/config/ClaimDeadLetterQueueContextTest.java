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
import com.titanium.customer.api.CustomerApi;
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
 * 🔴 <b>守护的三个组</b>（m6-909 建、m6-913 补齐赔付派发组、m6-915 补齐读侧投影组）：
 * <ul>
 *   <li>{@code claim-kafka-group}（{@link KafkaEventPublisher}）——跨域事件外发；</li>
 *   <li>{@code claim-settlement-group}（{@code ClaimSettlementPaymentSaga}）——理赔赔付派发；</li>
 *   <li>{@code claim-query-group}（{@code ClaimProjectionEventHandler} / {@code ClaimQueryHandler}）
 *       ——读侧投影，失败只表现为读模型停在旧值（不丢事件、但读到的状态陈旧）。</li>
 * </ul>
 * 三条链路的失败都只能靠「抛出 → 入 DLQ → {@code DeadLetterQueueService} 定时重投」兜底：
 * ① 组未注册（yml 键与 {@code @ProcessingGroup} 漂移）→ 处理器仍存在却退回默认配置，**不报错**；
 * ② 非 tracking（改回 subscribing）→ {@code SequencedDeadLetterQueue} 拿不到，失败即永久丢失；
 * ③ 未启用 DLQ（删 dlq 配置）→ {@code sequencedDeadLetterProcessor} 恒空，重投服务静默空转。
 * 三条都不会让构建失败，只能靠本类拦下。
 * </p>
 * <p>
 * 🔴 <b>「未声明」不等于「无害」（m6-915 上下文实测）</b>：本项目 eventBus 为流式
 * {@code EmbeddedEventStore}，Axon 对<b>未在 yml 声明的组一律按隐式 tracking 装配</b>
 * （实测 {@code claim-query-group} = {@code TrackingEventProcessor}），但该组<b>拿不到 DLQ</b>
 * （{@code sequencedDeadLetterProcessor} 为空）。即「不声明」既不报错、也不会退回同步，
 * 只会静默失去重投能力——而 {@code DeadLetterQueueService} 的重投清单里往往早已含该组，
 * 于是每次扫描都空转。本类对三个组一律要求显式声明，正是为了堵住这条静默路径。
 * </p>
 * <p>
 * ⚠️ tracking 的已知代价是「首启位点」：令牌不存在时从事件流<b>头部</b>开始消费。两个出站组的该风险
 * 由 application.yml 的 {@code titanium.axon.outbound-relay.groups} 登记消解（登记后首启取流末端），
 * 故改动其配置时须同步检查该登记项，二者是一组（已由 yml 解析测试守护）。读侧投影组
 * {@code claim-query-group} 则<b>刻意不登记</b>——读模型在全新部署时需要从头部重放重建（见
 * {@code AxonOutboundRelayAutoConfiguration} 的 javadoc），故三者的登记策略不同，不可一刀切。
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
        CustomerApi.class,
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
        assertGroupIsTrackingWithDeadLetterQueue(KafkaEventPublisher.PROCESSING_GROUP);
    }

    @Test
    @DisplayName("赔付派发组 claim-settlement-group：已注册 + tracking + DLQ 可用（m6-913 补齐）")
    void providesTrackingSettlementGroupWithDeadLetterQueue() {
        assertGroupIsTrackingWithDeadLetterQueue("claim-settlement-group");
    }

    @Test
    @DisplayName("读侧投影组 claim-query-group：已注册 + tracking + DLQ 可用（m6-915 补齐）")
    void providesTrackingQueryGroupWithDeadLetterQueue() {
        assertGroupIsTrackingWithDeadLetterQueue("claim-query-group");
    }

    /**
     * 断言某处理组按「tracking + DLQ」装配（三条断言各有分工，见类注释）。
     */
    private void assertGroupIsTrackingWithDeadLetterQueue(String processingGroup) {
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
