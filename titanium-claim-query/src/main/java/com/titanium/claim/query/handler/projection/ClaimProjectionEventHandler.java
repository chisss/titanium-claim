package com.titanium.claim.query.handler.projection;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.claim.common.enums.ClaimStatus;
import com.titanium.claim.event.ClaimAlertFlaggedEvent;
import com.titanium.claim.event.ClaimClosedEvent;
import com.titanium.claim.event.ClaimCreatedEvent;
import com.titanium.claim.event.ClaimLossAssessedEvent;
import com.titanium.claim.event.ClaimPaymentCompletedEvent;
import com.titanium.claim.event.ClaimRejectedEvent;
import com.titanium.claim.event.ClaimSettledEvent;
import com.titanium.claim.event.ClaimStatusChangedEvent;
import com.titanium.claim.event.ClaimSurveySubmittedEvent;
import com.titanium.claim.event.ClaimUpdatedEvent;
import com.titanium.claim.event.DeathBenefitSettledEvent;
import com.titanium.claim.event.DisabilityBenefitSettledEvent;
import com.titanium.claim.query.mapper.ClaimViewMapper;
import com.titanium.claim.query.repository.ClaimViewRepository;
import com.titanium.claim.query.view.ClaimView;
import com.titanium.metadata.enums.claim.ClaimEnum;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 理赔域读模型投影事件处理器（CQRS 读侧核心）
 * <p>
 * 订阅 claim 域领域事件，投影到读模型表 {@code t_claim_view}，实现读写分离。 只做「事件 → 读模型」写入，
 * 不发命令、不持有 CommandGateway（读侧编排越界禁止）。
 * </p>
 * <p>
 * <b>处理组</b>：{@code claim-query-group}，读侧投影 + 查询处理器 + DLQ 三处一致。
 * </p>
 * <p>
 * 新建型投影（{@link ClaimCreatedEvent}）的事件 → 读模型字段映射收敛到 {@link ClaimViewMapper}（MapStruct，
 * {@code @MappingTarget} upsert），消除逐字段 set；含事件时间/「仅首次」语义的审计时间戳保护逻辑保留在本处理器。
 * 增量更新型投影（更新/状态变更/查勘/定损/结算）仅少量字段变更，保持就地 set。
 * </p>
 */
@Slf4j
@Component
@ProcessingGroup("claim-query-group")
@RequiredArgsConstructor
public class ClaimProjectionEventHandler {

    private final ClaimViewRepository claimViewRepository;
    private final ClaimViewMapper     claimViewMapper;

    /**
     * 投影理赔创建事件：新建读模型记录
     */
    @EventHandler
    @Transactional
    public void on(ClaimCreatedEvent event) {
        log.info("[读模型投影] 理赔创建: claimId={}", event.claimId());

        ClaimView view = claimViewRepository.findByClaimId(event.claimId().value()).orElseGet(ClaimView::new);

        // 事件字段 → 读模型的结构映射收敛到 MapStruct（值对象拆解 + 初始状态常量），消除逐字段 set
        claimViewMapper.applyCreated(view, event);
        // 租户兜底：历史事件（tenantId 字段引入前产生）回落默认租户，避免投影重试队列卡死
        if (view.getTenantId() == null) {
            view.setTenantId(event.tenantId() != null ? event.tenantId() : "default-tenant");
        }
        stampAuditTime(view, event.createdAt());

        claimViewRepository.save(view);
    }

    /**
     * 投影理赔更新事件
     */
    @EventHandler
    @Transactional
    public void on(ClaimUpdatedEvent event) {
        log.info("[读模型投影] 理赔更新: claimId={}", event.claimId());

        claimViewRepository.findByClaimId(event.claimId().value()).ifPresentOrElse(view -> {
            view.setClaimType(event.claimType());
            view.setIncidentDate(event.incidentDate());
            view.setIncidentDescription(event.incidentDescription());
            if (event.claimAmount() != null) {
                view.setClaimAmount(event.claimAmount().value());
            }
            view.setUpdateTime(event.updatedAt());
            claimViewRepository.save(view);
        }, () -> log.warn("[读模型投影] 理赔更新失败：未找到读模型记录 claimId={}", event.claimId()));
    }

    /**
     * 投影理赔状态变更事件
     */
    @EventHandler
    @Transactional
    public void on(ClaimStatusChangedEvent event) {
        log.info("[读模型投影] 理赔状态变更: claimId={}, {} -> {}", event.claimId(), event.oldStatus(),
                event.newStatus());

        claimViewRepository.findByClaimId(event.claimId().value()).ifPresentOrElse(view -> {
            view.setStatus(event.newStatus());
            view.setUpdateTime(event.changedAt());
            claimViewRepository.save(view);
        }, () -> log.warn("[读模型投影] 理赔状态变更失败：未找到读模型记录 claimId={}", event.claimId()));
    }

    /**
     * 投影查勘提交事件（阶段推进至 SURVEY）
     */
    @EventHandler
    @Transactional
    public void on(ClaimSurveySubmittedEvent event) {
        log.info("[读模型投影] 查勘提交: claimId={}, phase={}", event.claimId(), event.newPhase());

        claimViewRepository.findByClaimId(event.claimId().value()).ifPresentOrElse(view -> {
            view.setPhase(event.newPhase());
            view.setUpdateTime(event.submittedAt());
            claimViewRepository.save(view);
        }, () -> log.warn("[读模型投影] 查勘提交失败：未找到读模型记录 claimId={}", event.claimId()));
    }

    /**
     * 投影定损提交事件（阶段推进至 LOSS_ASSESS）
     */
    @EventHandler
    @Transactional
    public void on(ClaimLossAssessedEvent event) {
        log.info("[读模型投影] 定损提交: claimId={}, phase={}", event.claimId(), event.newPhase());

        claimViewRepository.findByClaimId(event.claimId().value()).ifPresentOrElse(view -> {
            view.setPhase(event.newPhase());
            view.setUpdateTime(event.assessedAt());
            claimViewRepository.save(view);
        }, () -> log.warn("[读模型投影] 定损提交失败：未找到读模型记录 claimId={}", event.claimId()));
    }

    /**
     * 投影核赔结算事件（记录核定赔付金额并进入赔付中，保持 APPROVED 待支付域回写）
     */
    @EventHandler
    @Transactional
    public void on(ClaimSettledEvent event) {
        log.info("[读模型投影] 核赔结算: claimId={}", event.claimId());

        claimViewRepository.findByClaimId(event.claimId().value()).ifPresentOrElse(view -> {
            if (event.settlement() != null) {
                view.setSettledAmount(event.settlement().settledAmount());
            }
            view.setPaymentStatus(ClaimEnum.PaymentStatus.PROCESSING);
            view.setUpdateTime(event.settledAt());
            claimViewRepository.save(view);
        }, () -> log.warn("[读模型投影] 核赔结算失败：未找到读模型记录 claimId={}", event.claimId()));
    }

    /**
     * 投影身故给付结算事件（记录给付总额并进入赔付中，保持 APPROVED 待支付域回写）
     */
    @EventHandler
    @Transactional
    public void on(DeathBenefitSettledEvent event) {
        log.info("[读模型投影] 身故给付结算: claimId={}, policyId={}", event.claimId(), event.policyId());

        claimViewRepository.findByClaimId(event.claimId().value()).ifPresentOrElse(view -> {
            if (event.settlement() != null) {
                view.setSettledAmount(event.settlement().settledAmount());
            }
            view.setPaymentStatus(ClaimEnum.PaymentStatus.PROCESSING);
            view.setUpdateTime(event.settledAt());
            claimViewRepository.save(view);
        }, () -> log.warn("[读模型投影] 身故给付结算失败：未找到读模型记录 claimId={}", event.claimId()));
    }

    /**
     * 投影全残给付结算事件（CLAIM-6：settled_amount 取给付总额，进入赔付中）
     */
    @EventHandler
    @Transactional
    public void on(DisabilityBenefitSettledEvent event) {
        log.info("[读模型投影] 全残给付结算: claimId={}, policyId={}", event.claimId(), event.policyId());

        claimViewRepository.findByClaimId(event.claimId().value()).ifPresentOrElse(view -> {
            if (event.settlement() != null) {
                view.setSettledAmount(event.settlement().settledAmount());
            }
            view.setPaymentStatus(ClaimEnum.PaymentStatus.PROCESSING);
            view.setUpdateTime(event.settledAt());
            claimViewRepository.save(view);
        }, () -> log.warn("[读模型投影] 全残给付结算失败：未找到读模型记录 claimId={}", event.claimId()));
    }

    /**
     * 投影警示打标事件（刷新读模型 alertFlags 列：AlertType code 逗号分隔，
     * 快赔通道判据「无欺诈警示标记」的数据来源）
     */
    @EventHandler
    @Transactional
    public void on(ClaimAlertFlaggedEvent event) {
        log.info("[读模型投影] 理赔警示打标: claimId={}, flags={}", event.claimId(), event.flags());

        claimViewRepository.findByClaimId(event.claimId().value()).ifPresentOrElse(view -> {
            String flagCodes = event.flags().stream()
                    .map(flag -> flag.type().getCode())
                    .collect(Collectors.joining(","));
            view.setAlertFlags(flagCodes);
            view.setUpdateTime(event.flaggedAt());
            claimViewRepository.save(view);
        }, () -> log.warn("[读模型投影] 理赔警示打标失败：未找到读模型记录 claimId={}", event.claimId()));
    }

    /**
     * 投影拒赔事件（流转至 REJECTED，记录拒赔原因与时间）
     */
    @EventHandler
    @Transactional
    public void on(ClaimRejectedEvent event) {
        log.info("[读模型投影] 理赔拒赔: claimId={}, reason={}", event.claimId(), event.reason());

        claimViewRepository.findByClaimId(event.claimId().value()).ifPresentOrElse(view -> {
            view.setStatus(ClaimStatus.REJECTED);
            view.setRejectionReason(event.reason() == null ? null : event.reason().getCode());
            view.setRejectedAt(event.rejectedAt());
            view.setPaymentStatus(ClaimEnum.PaymentStatus.REJECTED_CLOSED);
            view.setUpdateTime(event.rejectedAt());
            claimViewRepository.save(view);
        }, () -> log.warn("[读模型投影] 理赔拒赔失败：未找到读模型记录 claimId={}", event.claimId()));
    }

    /**
     * 投影赔付完成事件（支付域回写：流转至 PAID，记录支付单号）
     */
    @EventHandler
    @Transactional
    public void on(ClaimPaymentCompletedEvent event) {
        log.info("[读模型投影] 赔付完成: claimId={}, paymentNo={}", event.claimId(), event.paymentNo());

        claimViewRepository.findByClaimId(event.claimId().value()).ifPresentOrElse(view -> {
            view.setStatus(ClaimStatus.PAID);
            view.setPaymentStatus(ClaimEnum.PaymentStatus.SUCCESS);
            view.setPaymentNo(event.paymentNo());
            view.setUpdateTime(event.paidAt());
            claimViewRepository.save(view);
        }, () -> log.warn("[读模型投影] 赔付完成失败：未找到读模型记录 claimId={}", event.claimId()));
    }

    /**
     * 投影结案事件（终态归档至 CLOSED，记录结案时间）
     */
    @EventHandler
    @Transactional
    public void on(ClaimClosedEvent event) {
        log.info("[读模型投影] 理赔结案: claimId={}", event.claimId());

        claimViewRepository.findByClaimId(event.claimId().value()).ifPresentOrElse(view -> {
            view.setStatus(ClaimStatus.CLOSED);
            view.setClosedAt(event.closedAt());
            view.setUpdateTime(event.closedAt());
            claimViewRepository.save(view);
        }, () -> log.warn("[读模型投影] 理赔结案失败：未找到读模型记录 claimId={}", event.claimId()));
    }

    /**
     * 统一填充读模型审计时间戳：createTime 仅首次创建时写入、updateTime 每次投影刷新。
     * <p>
     * 该逻辑含「仅首次设置」语义、且以事件时间 {@code eventTime} 为基准（缺省回退当前时间），
     * 属投影处理器职责，不下沉 MapStruct 映射器。
     * </p>
     */
    private void stampAuditTime(ClaimView view, LocalDateTime eventTime) {
        LocalDateTime stamp = eventTime != null ? eventTime : LocalDateTime.now();
        if (view.getCreateTime() == null) {
            view.setCreateTime(stamp);
        }
        view.setUpdateTime(stamp);
    }
}
