package com.titanium.claim.application.saga;

import java.time.LocalDateTime;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;

import com.titanium.claim.application.saga.assembler.ClaimClosureDocumentAssembler;
import com.titanium.claim.event.ClaimRejectedEvent;
import com.titanium.claim.event.ClaimSettledEvent;
import com.titanium.claim.event.DeathBenefitSettledEvent;
import com.titanium.claim.event.DisabilityBenefitSettledEvent;
import com.titanium.claim.port.document.DocumentServicePort;
import com.titanium.claim.valueobject.ClaimSettlement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 理赔结案单证集成编排（结案点 → 文档域理赔结案单证派发）
 * <p>
 * 监听本域四类结案事件（普通赔付 / 身故给付 / 全残给付 / 拒赔），经 {@link DocumentServicePort}
 * 派发结案单证要素到 Kafka {@code claim-closed} 主题，由 document 域防腐消费后渲染、落盘并建档。
 * 此前 {@code DocumentServicePort} 全仓零实现零调用——<b>理赔结案不产生任何单证</b>（规划 §6-2/§8-1 缺口）。
 * </p>
 * <p>
 * <b>形态说明</b>：单步跨服务派发、无本地多步状态与补偿需求，故用 {@code @EventHandler} 事件驱动形态
 * 而非 Axon {@code @Saga} 注解（无 StartSaga/关联键跟踪的必要），与同包
 * {@link ClaimSettlementPaymentSaga} 同构。🔴 <b>不在领域服务内发消息</b>：编排属应用层职责，
 * 领域服务保持无 Port 依赖的纯净性（根规约 §3.4.4）。
 * </p>
 * <p>
 * 🔴 <b>处理组复用 {@code claim-settlement-group}</b>：与 {@link ClaimSettlementPaymentSaga} 同组——
 * 二者都是「结算事件 → 跨服务派发」，可靠性要求（tracking + DLQ + 首启位点取事件流末端）完全一致。
 * 复用既有组可避免新增一处必须与 {@code axon.eventhandling.processors} 和
 * {@code titanium.axon.outbound-relay.groups} 两处 yml 配置对齐的组名（该对齐一旦漂移不报错，
 * 只会静默失去 DLQ 与位点登记，见 {@code ClaimAxonProcessingGroupConfigurationTest} 守护的 m6-913 缺陷）。
 * </p>
 * <p>
 * <b>幂等</b>：本端口为 at-least-once（DLQ 重投 / Kafka 重投均会重复投递），下游 document 域按
 * 「一次投递 → 一份单证」处理；重复投递会生成重复单证的窗口与既有 {@code titanium.policy.issued}
 * 出单证链路一致，属该域既有语义，本次不引入新的幂等机制。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ProcessingGroup("claim-settlement-group")
public class ClaimClosureDocumentSaga {

    private final DocumentServicePort           documentServicePort;
    private final ClaimClosureDocumentAssembler claimClosureDocumentAssembler;

    /** 普通赔付核赔结算完成 → 派发结案单证（有赔付事实） */
    @EventHandler
    public void on(ClaimSettledEvent event) {
        log.info("[结案单证] 核赔结算完成, claimId={}, 核定赔付金额={}", event.claimId(),
                event.settlement() == null ? null : event.settlement().settledAmount());
        documentServicePort.archiveClaimDocument(claimClosureDocumentAssembler.fromSettlement(event.claimId().value(),
                event.policyId(), event.settlement(), event.settledAt(), event.tenantId()));
    }

    /** 身故给付结算完成 → 派发结案单证（与普通赔付同构，差异仅在给付额来源） */
    @EventHandler
    public void on(DeathBenefitSettledEvent event) {
        log.info("[结案单证] 身故给付结算完成, claimId={}, policyId={}", event.claimId(), event.policyId());
        dispatchSettlement(event.claimId().value(), event.policyId(), event.settlement(), event.settledAt(),
                event.tenantId());
    }

    /** 全残给付结算完成 → 派发结案单证（与身故给付同构） */
    @EventHandler
    public void on(DisabilityBenefitSettledEvent event) {
        log.info("[结案单证] 全残给付结算完成, claimId={}, policyId={}", event.claimId(), event.policyId());
        dispatchSettlement(event.claimId().value(), event.policyId(), event.settlement(), event.settledAt(),
                event.tenantId());
    }

    /**
     * 拒赔结案 → 派发结案单证（无赔付事实，金额与给付方式留空）。
     * <p>
     * 面向客户的拒赔通知书由 notification 域经 {@code claim-rejected} 主题独立承接（m6-905 已闭环）；
     * 本单证是<b>归档件</b>，落在单证域供赔案卷宗查询，二者职责不同、互不替代。
     * </p>
     */
    @EventHandler
    public void on(ClaimRejectedEvent event) {
        log.info("[结案单证] 拒赔结案, claimId={}, reason={}", event.claimId(),
                event.reason() == null ? null : event.reason().getCode());
        documentServicePort.archiveClaimDocument(claimClosureDocumentAssembler.fromRejection(event.claimId().value(),
                event.policyId(), event.reason(), event.comment(), event.rejectedAt(), event.tenantId()));
    }

    /** 结算类结案单证派发（身故 / 全残共用，避免两份同构实现各自漂移） */
    private void dispatchSettlement(String claimId, String policyId, ClaimSettlement settlement,
            LocalDateTime closedAt, String tenantId) {
        documentServicePort.archiveClaimDocument(claimClosureDocumentAssembler.fromSettlement(claimId, policyId,
                settlement, closedAt, tenantId));
    }
}
