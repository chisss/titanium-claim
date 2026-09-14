package com.titanium.claim.aggregate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

import com.titanium.claim.command.ChangeClaimStatusCommand;
import com.titanium.claim.command.CloseClaimCommand;
import com.titanium.claim.command.CompletePaymentCommand;
import com.titanium.claim.command.CreateClaimCommand;
import com.titanium.claim.command.FlagClaimAlertCommand;
import com.titanium.claim.command.RecordPaymentFailureCommand;
import com.titanium.claim.command.RejectClaimCommand;
import com.titanium.claim.command.SettleClaimCommand;
import com.titanium.claim.command.SettleDeathBenefitCommand;
import com.titanium.claim.command.SettleDisabilityBenefitCommand;
import com.titanium.claim.command.SubmitLossAssessmentCommand;
import com.titanium.claim.command.SubmitSurveyCommand;
import com.titanium.claim.command.UpdateClaimCommand;
import com.titanium.claim.common.enums.ClaimStatus;
import com.titanium.claim.common.enums.PaymentFailureType;
import com.titanium.claim.event.ClaimAlertFlaggedEvent;
import com.titanium.claim.event.ClaimClosedEvent;
import com.titanium.claim.event.ClaimCreatedEvent;
import com.titanium.claim.event.ClaimLossAssessedEvent;
import com.titanium.claim.event.ClaimPaymentCompletedEvent;
import com.titanium.claim.event.ClaimPaymentFailedEvent;
import com.titanium.claim.event.ClaimRejectedEvent;
import com.titanium.claim.event.ClaimSettledEvent;
import com.titanium.claim.event.ClaimStatusChangedEvent;
import com.titanium.claim.event.ClaimSurveySubmittedEvent;
import com.titanium.claim.event.ClaimUpdatedEvent;
import com.titanium.claim.event.DeathBenefitSettledEvent;
import com.titanium.claim.event.DisabilityBenefitSettledEvent;
import com.titanium.claim.exception.ClaimPhaseTransitionException;
import com.titanium.claim.exception.ClaimSettlementAmountException;
import com.titanium.claim.exception.ClaimStatusPreconditionException;
import com.titanium.claim.exception.ClaimStatusTransitionException;
import com.titanium.claim.valueobject.AlertFlag;
import com.titanium.claim.valueobject.BenefitCalculation;
import com.titanium.claim.valueobject.ClaimAmount;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.ClaimSettlement;
import com.titanium.claim.valueobject.CustomerId;
import com.titanium.claim.valueobject.DeathClaimEvidence;
import com.titanium.claim.valueobject.DisabilityClaimEvidence;
import com.titanium.claim.valueobject.LossAssessment;
import com.titanium.claim.valueobject.PolicyId;
import com.titanium.claim.valueobject.Survey;
import com.titanium.common.domain.BaseAggregate;
import com.titanium.metadata.enums.claim.ClaimEnum;
import com.titanium.metadata.enums.claim.ClaimPhase;
import com.titanium.metadata.enums.claim.RejectReason;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * 理赔聚合根
 * <p>
 * 管理理赔案件全生命周期：报案(PENDING) → 处理(PROCESSING) → 核赔通过(APPROVED) →
 * 结算(赔付中) → 赔付(PAID) → 结案(CLOSED)；PENDING/PROCESSING 阶段可拒赔(REJECTED)，
 * 终态(PAID/REJECTED)可结案归档。核赔结算(settle)记录赔付结论并进入赔付中，
 * 由支付域出账成功回写 {@link CompletePaymentCommand} 后流转至 PAID。
 * </p>
 */
@Aggregate
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
public class Claim extends BaseAggregate {
    @AggregateIdentifier
    private ClaimId             claimId;
    private CustomerId          customerId;
    private PolicyId            policyId;
    private String              claimNumber;
    private ClaimEnum.ClaimType claimType;
    private LocalDateTime       incidentDate;
    private String              incidentDescription;
    private ClaimAmount         claimAmount;
    private ClaimStatus         status;
    private ClaimSettlement     settlement;
    /** 赔付状态（结算后进入赔付中，支付域出账成功回写为成功） */
    private ClaimEnum.PaymentStatus paymentStatus;
    /** 支付单号（支付域回写，供对账） */
    private String              paymentNo;
    /** 赔付失败类型（支付域出账未成功回写；对端 code 无法识别时为 null） */
    private PaymentFailureType  paymentFailureType;
    /** 赔付失败原因（支付域出账未成功回写：渠道失败原因或人工取消原因） */
    private String              paymentFailureReason;
    /** 赔付失败时间 */
    private LocalDateTime       paymentFailedAt;
    /** 拒赔原因（拒赔时记录） */
    private RejectReason        rejectionReason;
    /** 拒赔时间 */
    private LocalDateTime       rejectedAt;
    /** 结案时间 */
    private LocalDateTime       closedAt;
    /** 理赔处理阶段（报案→查勘→定损→核赔→结算→赔付） */
    private ClaimPhase          phase;
    /** 查勘记录（车险/调查类案件） */
    private Survey              survey;
    /** 定损记录（车险按损案件） */
    private LossAssessment      lossAssessment;
    /** 身故证据材料（寿险身故理赔专属） */
    private DeathClaimEvidence  deathEvidence;
    /** 身故给付金核算（寿险身故理赔专属，按受益人份额分配） */
    private BenefitCalculation  benefitCalculation;
    /** 全残证据材料（寿险/意外险全残理赔专属，CLAIM-6） */
    private DisabilityClaimEvidence disabilityEvidence;
    /** 全残给付金核算（寿险/意外险全残理赔专属，按受益人份额分配） */
    private BenefitCalculation  disabilityBenefitCalculation;
    /** 反欺诈警示与统计口径标记（延迟报案/多次报案/风险评分/快赔，快赔通道判据的数据来源） */
    private List<AlertFlag>     alertFlags = new ArrayList<>();
    /**
     * 租户ID（由 {@link ClaimCreatedEvent} 回放填充）
     * <p>
     * 聚合自身不做租户决策，持有它是为了<b>把租户贯穿进后续所有对外事件</b>：消费端（policy 域保单终止、
     * 通知域拒赔通知）据此判定事件归属租户。历史事件流无此字段时回放为 null，消费端须容忍。
     * </p>
     */
    private String              tenantId;

    @CommandHandler
    public Claim(CreateClaimCommand command) {
        AggregateLifecycle.apply(new ClaimCreatedEvent(command.claimId(), command.customerId(), command.policyId(),
                command.claimNumber(), command.claimType(), command.incidentDate(), command.incidentDescription(),
                command.claimAmount(), LocalDateTime.now(), command.tenantId()));
    }

    @CommandHandler
    public void handle(UpdateClaimCommand command) {
        AggregateLifecycle.apply(new ClaimUpdatedEvent(command.claimId(), command.claimType(), command.incidentDate(),
                command.incidentDescription(), command.claimAmount(), LocalDateTime.now(), this.tenantId));
    }

    /**
     * 打标警示标记（反欺诈警示 + 统计口径标记）：按类型合并去重后发布事件投影至读模型，
     * 供快赔通道判据「无欺诈警示标记」使用。幂等：全部标记均已存在时不重复发布。
     */
    @CommandHandler
    public void handle(FlagClaimAlertCommand command) {
        List<AlertFlag> merged = mergeAlertFlags(command.flags());
        if (merged.size() == this.alertFlags.size()) {
            return; // 幂等：无新增标记
        }
        AggregateLifecycle.apply(new ClaimAlertFlaggedEvent(command.claimId(), merged, LocalDateTime.now()));
    }

    /**
     * 按类型合并去重：新标记类型不存在于既有标记时追加，已存在则忽略（保留首次命中的规则标识）。
     */
    private List<AlertFlag> mergeAlertFlags(List<AlertFlag> flags) {
        List<AlertFlag> merged = new ArrayList<>(this.alertFlags);
        if (flags == null || flags.isEmpty()) {
            return merged;
        }
        for (AlertFlag flag : flags) {
            boolean exists = merged.stream().anyMatch(existing -> existing.type() == flag.type());
            if (!exists) {
                merged.add(flag);
            }
        }
        return merged;
    }

    @CommandHandler
    public void handle(ChangeClaimStatusCommand command) {
        if (status == null || status == command.newStatus()) {
            return;
        }
        // 状态机合法性校验：禁止非法跳转（拒赔/赔付/结案走专用命令，不经此通用通道）
        validateTransition(status, command.newStatus());
        AggregateLifecycle.apply(new ClaimStatusChangedEvent(command.claimId(), status, command.newStatus(),
                command.reason(), LocalDateTime.now(), this.tenantId));
    }

    /**
     * 核赔结算：仅 APPROVED 状态可结算，记录赔付结论并进入赔付中，
     * 待支付域出账成功回写 {@link CompletePaymentCommand} 后流转至 PAID。
     * <p>
     * 核定赔付金额经 {@link #resolveSettledAmount(ClaimId, BigDecimal)} 核定——定损在案的案件以定损核定额为准，
     * 调用方不得透传改数（详见 {@link SettleClaimCommand} 的来源规则）。
     * </p>
     */
    @CommandHandler
    public void handle(SettleClaimCommand command) {
        if (status != ClaimStatus.APPROVED) {
            throw new ClaimStatusPreconditionException(command.claimId(), status, "核赔结算", "APPROVED");
        }
        if (paymentStatus == ClaimEnum.PaymentStatus.PROCESSING) {
            // 结算后状态保持 APPROVED 待支付域回写，须以赔付状态挡住重复结算
            throw ClaimStatusPreconditionException.alreadySettled(command.claimId(), "核赔结算");
        }
        BigDecimal settledAmount = resolveSettledAmount(command.claimId(), command.settledAmount());
        ClaimSettlement claimSettlement = ClaimSettlement.of(settledAmount, command.payoutMethod(),
                command.payeeAccount(), command.conclusion());
        AggregateLifecycle.apply(new ClaimSettledEvent(command.claimId(), this.policyId.value(), claimSettlement,
                LocalDateTime.now(), this.tenantId));
    }

    /**
     * 核定赔付金额：定损在案的案件取定损核定额（唯一权威来源），未定损案件取调用方指定金额。
     *
     * @param claimId            理赔案件ID
     * @param providedAmount     调用方指定金额（可为 null）
     * @return 核定后的赔付金额
     */
    private BigDecimal resolveSettledAmount(ClaimId claimId, BigDecimal providedAmount) {
        if (lossAssessment == null) {
            if (providedAmount == null) {
                throw ClaimSettlementAmountException.required(claimId);
            }
            return providedAmount;
        }
        BigDecimal assessedAmount = lossAssessment.payableAmount();
        if (assessedAmount == null || assessedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw ClaimSettlementAmountException.nonPositive(claimId, assessedAmount);
        }
        if (providedAmount != null && providedAmount.compareTo(assessedAmount) != 0) {
            throw ClaimSettlementAmountException.mismatch(claimId, assessedAmount, providedAmount);
        }
        return assessedAmount;
    }

    /**
     * 按定损核定的应赔金额（=（定损总金额−残值）×责任比例）。
     * <p>
     * 供结案环节带出「按定损应赔金额」——未定损案件返回 {@code null}，由调用方区分「有定损依据」与「无依据」，
     * 不得以 0 冒充。
     * </p>
     *
     * @return 定损核定额；未定损时为 {@code null}
     */
    public BigDecimal assessedPayableAmount() {
        return lossAssessment == null ? null : lossAssessment.payableAmount();
    }

    /**
     * 身故给付结算（寿险身故理赔专属）：仅 APPROVED 状态可给付，须身故材料齐备，
     * 记录身故证据/受益人核算并进入赔付中，发布 {@link DeathBenefitSettledEvent} 触发下游保单终止。
     * <p>
     * 区别于通用核赔结算：以身故金核算总额为给付额，给付后保单责任终止（被保险人身故）。
     * </p>
     */
    @CommandHandler
    public void handle(SettleDeathBenefitCommand command) {
        if (status != ClaimStatus.APPROVED) {
            throw new ClaimStatusPreconditionException(command.claimId(), status, "身故给付结算", "APPROVED");
        }
        if (paymentStatus == ClaimEnum.PaymentStatus.PROCESSING) {
            // 结算后状态保持 APPROVED 待支付域回写，须以赔付状态挡住重复给付
            throw ClaimStatusPreconditionException.alreadySettled(command.claimId(), "身故给付结算");
        }
        if (this.claimType != ClaimEnum.ClaimType.DEATH) {
            throw new ClaimStatusPreconditionException(command.claimId(), status, "身故给付结算", "DEATH 类型案件");
        }
        if (command.evidence() == null || !command.evidence().isComplete()) {
            throw new ClaimStatusPreconditionException(command.claimId(), status, "身故给付结算", "身故材料齐备");
        }
        if (command.benefitCalculation() == null) {
            throw new ClaimStatusPreconditionException(command.claimId(), status, "身故给付结算", "受益人份额核算");
        }
        // 给付额取受益人份额核算的给付总额，收款方留空（按份额分账由支付域按受益人明细处理）
        ClaimSettlement deathSettlement = ClaimSettlement.of(command.benefitCalculation().totalBenefit(),
                command.payoutMethod(), null, command.conclusion());
        AggregateLifecycle.apply(new DeathBenefitSettledEvent(command.claimId(), this.policyId.value(),
                command.evidence(), command.benefitCalculation(), deathSettlement, LocalDateTime.now(), this.tenantId));
    }

    /**
     * 全残给付结算（寿险/意外险全残理赔专属，CLAIM-6）：仅 APPROVED 状态可给付，须全残材料齐备，
     * 记录全残证据/受益人核算并进入赔付中，发布 {@link DisabilityBenefitSettledEvent} 触发下游保单终止。
     * <p>
     * 与身故给付同构：以全残给付金核算总额为给付额（基本保额、或账户价值与基本保额孰高，
     * 按条款来源精算），给付后保单责任终止（被保险人全残）。
     * </p>
     */
    @CommandHandler
    public void handle(SettleDisabilityBenefitCommand command) {
        if (status != ClaimStatus.APPROVED) {
            throw new ClaimStatusPreconditionException(command.claimId(), status, "全残给付结算", "APPROVED");
        }
        if (paymentStatus == ClaimEnum.PaymentStatus.PROCESSING) {
            // 结算后状态保持 APPROVED 待支付域回写，须以赔付状态挡住重复给付
            throw ClaimStatusPreconditionException.alreadySettled(command.claimId(), "全残给付结算");
        }
        if (this.claimType != ClaimEnum.ClaimType.DISABILITY) {
            throw new ClaimStatusPreconditionException(command.claimId(), status, "全残给付结算", "DISABILITY 类型案件");
        }
        if (command.evidence() == null || !command.evidence().isComplete()) {
            throw new ClaimStatusPreconditionException(command.claimId(), status, "全残给付结算", "全残材料齐备");
        }
        if (command.benefitCalculation() == null) {
            throw new ClaimStatusPreconditionException(command.claimId(), status, "全残给付结算", "受益人份额核算");
        }
        // 给付额取受益人份额核算的给付总额，收款方留空（按份额分账由支付域按受益人明细处理）
        ClaimSettlement disabilitySettlement = ClaimSettlement.of(command.benefitCalculation().totalBenefit(),
                command.payoutMethod(), null, command.conclusion());
        AggregateLifecycle.apply(new DisabilityBenefitSettledEvent(command.claimId(), this.policyId.value(),
                command.evidence(), command.benefitCalculation(), disabilitySettlement, LocalDateTime.now(),
                this.tenantId));
    }

    /**
     * 拒赔：仅 PENDING/PROCESSING 阶段可拒赔（已核赔通过的 APPROVED 不可反悔，终态不可拒赔），
     * 记录拒赔原因并发布 {@link ClaimRejectedEvent} 触发拒赔通知书发送。
     */
    @CommandHandler
    public void handle(RejectClaimCommand command) {
        if (status == ClaimStatus.REJECTED) {
            return; // 幂等：已拒赔案件重复指令直接忽略
        }
        if (status != ClaimStatus.PENDING && status != ClaimStatus.PROCESSING) {
            throw new ClaimStatusPreconditionException(command.claimId(), status, "拒赔", "PENDING/PROCESSING");
        }
        AggregateLifecycle.apply(new ClaimRejectedEvent(command.claimId(), this.policyId == null ? null
                : this.policyId.value(), this.customerId == null ? null : this.customerId.value(), command.reason(),
                command.comment(), LocalDateTime.now(), this.tenantId));
    }

    /**
     * 赔付完成回写：仅 APPROVED 且已结算（赔付中）的案件可回写，
     * 支付域出账成功后置 PAID 并记录支付单号。
     */
    @CommandHandler
    public void handle(CompletePaymentCommand command) {
        if (status != ClaimStatus.APPROVED || settlement == null) {
            throw new ClaimStatusPreconditionException(command.claimId(), status, "赔付完成回写", "APPROVED 且已结算");
        }
        AggregateLifecycle
                .apply(new ClaimPaymentCompletedEvent(command.claimId(), command.paymentNo(), LocalDateTime.now()));
    }

    /**
     * 赔付失败回写：支付域出账未成功（渠道确认失败 / 人工取消）后标记赔付失败。
     * <p>
     * 🔴 <b>幂等且容忍乱序</b>：成功与未成功分属两个 Kafka 主题，跨主题**无保序保证**——人工重派出款时，
     * 「后一类消息」可能先于「更早的未成功消息」到达。故仅「已结算赔付中」的案件可标记失败，
     * 其余一律静默忽略：**不抛异常**，否则 Kafka 会无限重投一条永远不可能成功的消息。
     * </p>
     * <p>
     * 案件状态**保持 APPROVED**：赔付决定不因出款受阻而改变，赔案仍待人工重派；
     * 重派成功后 {@link CompletePaymentCommand} 照常回写至 PAID。
     * </p>
     */
    @CommandHandler
    public void handle(RecordPaymentFailureCommand command) {
        if (status != ClaimStatus.APPROVED || settlement == null
                || paymentStatus != ClaimEnum.PaymentStatus.PROCESSING) {
            return;
        }
        AggregateLifecycle.apply(new ClaimPaymentFailedEvent(command.claimId(), command.paymentNo(),
                PaymentFailureType.fromCode(command.failureType()), command.failureReason(), LocalDateTime.now()));
    }

    /**
     * 结案归档：仅终态（PAID/REJECTED）案件可结案，流转至 CLOSED。
     */
    @CommandHandler
    public void handle(CloseClaimCommand command) {
        if (status != ClaimStatus.PAID && status != ClaimStatus.REJECTED) {
            throw new ClaimStatusPreconditionException(command.claimId(), status, "结案归档", "PAID/REJECTED");
        }
        AggregateLifecycle.apply(new ClaimClosedEvent(command.claimId(), LocalDateTime.now()));
    }

    /**
     * 提交查勘：推进理赔阶段至 SURVEY（车险/调查类案件）。 须在 REPORT 阶段之后、APPROVAL 之前。
     */
    @CommandHandler
    public void handle(SubmitSurveyCommand command) {
        ensurePhaseBefore(ClaimPhase.SURVEY, ClaimPhase.APPROVAL);
        AggregateLifecycle.apply(new ClaimSurveySubmittedEvent(command.claimId(), command.survey(), ClaimPhase.SURVEY,
                LocalDateTime.now()));
    }

    /**
     * 提交定损：推进理赔阶段至 LOSS_ASSESS（车险按损案件）。 须已完成查勘（SURVEY 阶段）。
     */
    @CommandHandler
    public void handle(SubmitLossAssessmentCommand command) {
        if (this.phase != ClaimPhase.SURVEY) {
            throw new ClaimPhaseTransitionException(command.claimId(),
                    this.phase == null ? ClaimPhase.REPORT : this.phase, ClaimPhase.LOSS_ASSESS);
        }
        AggregateLifecycle.apply(new ClaimLossAssessedEvent(command.claimId(), command.lossAssessment(),
                ClaimPhase.LOSS_ASSESS, LocalDateTime.now()));
    }

    /**
     * 阶段流转校验：目标阶段须晚于当前阶段且不超过上限。
     *
     * @param target 目标阶段
     * @param ceiling 阶段上限（不含）
     */
    private void ensurePhaseBefore(ClaimPhase target, ClaimPhase ceiling) {
        ClaimPhase current = this.phase == null ? ClaimPhase.REPORT : this.phase;
        if (current.getEnumCode() >= ceiling.getEnumCode() || target.getEnumCode() <= current.getEnumCode()) {
            throw new ClaimPhaseTransitionException(this.claimId, current, target);
        }
    }

    /**
     * 理赔状态流转合法性校验（通用状态变更通道专用）。
     * <p>
     * 合法流转：PENDING→PROCESSING；PROCESSING→APPROVED。
     * 拒赔(PENDING/PROCESSING→REJECTED)走 {@link RejectClaimCommand}、
     * 赔付(APPROVED+已结算→PAID)走 {@link CompletePaymentCommand}、
     * 结案(PAID/REJECTED→CLOSED)走 {@link CloseClaimCommand}，均不经此通用通道。
     * </p>
     */
    private void validateTransition(ClaimStatus from, ClaimStatus to) {
        boolean legal = switch (from) {
            case PENDING -> to == ClaimStatus.PROCESSING;
            case PROCESSING -> to == ClaimStatus.APPROVED;
            case APPROVED, PAID, REJECTED, CLOSED -> false;
        };
        if (!legal) {
            throw new ClaimStatusTransitionException(this.claimId, from, to);
        }
    }

    @EventSourcingHandler
    protected void on(ClaimCreatedEvent event) {
        this.tenantId = event.tenantId();
        this.claimId = event.claimId();
        this.customerId = event.customerId();
        this.policyId = event.policyId();
        this.claimNumber = event.claimNumber();
        this.claimType = event.claimType();
        this.incidentDate = event.incidentDate();
        this.incidentDescription = event.incidentDescription();
        this.claimAmount = event.claimAmount();
        this.status = ClaimStatus.PENDING;
        this.phase = ClaimPhase.REPORT;
        this.createTime = event.createdAt();
        this.updateTime = event.createdAt();
    }

    @EventSourcingHandler
    protected void on(ClaimSurveySubmittedEvent event) {
        this.survey = event.survey();
        this.phase = event.newPhase();
        this.updateTime = event.submittedAt();
    }

    @EventSourcingHandler
    protected void on(ClaimLossAssessedEvent event) {
        this.lossAssessment = event.lossAssessment();
        this.phase = event.newPhase();
        this.updateTime = event.assessedAt();
    }

    @EventSourcingHandler
    protected void on(ClaimUpdatedEvent event) {
        this.claimType = event.claimType();
        this.incidentDate = event.incidentDate();
        this.incidentDescription = event.incidentDescription();
        this.claimAmount = event.claimAmount();
        this.updateTime = event.updatedAt();
    }

    @EventSourcingHandler
    protected void on(ClaimAlertFlaggedEvent event) {
        this.alertFlags = new ArrayList<>(event.flags());
        this.updateTime = event.flaggedAt();
    }

    @EventSourcingHandler
    protected void on(ClaimStatusChangedEvent event) {
        this.status = event.newStatus();
        // 核赔通过即进入 APPROVAL 阶段——通用状态通道是 APPROVED 的唯一入口
        // （validateTransition: PROCESSING -> APPROVED），阶段自此不再回退
        if (event.newStatus() == ClaimStatus.APPROVED) {
            this.phase = ClaimPhase.APPROVAL;
        }
        this.updateTime = event.changedAt();
    }

    @EventSourcingHandler
    protected void on(ClaimSettledEvent event) {
        this.settlement = event.settlement();
        // 结算后进入赔付中，保持 APPROVED 待支付域出账回写，不再直接置 PAID
        this.paymentStatus = ClaimEnum.PaymentStatus.PROCESSING;
        this.phase = ClaimPhase.SETTLEMENT;
        this.updateTime = event.settledAt();
    }

    @EventSourcingHandler
    protected void on(DeathBenefitSettledEvent event) {
        this.deathEvidence = event.evidence();
        this.benefitCalculation = event.benefitCalculation();
        this.settlement = event.settlement();
        // 身故给付结算后进入赔付中，保持 APPROVED 待支付域出账回写，不再直接置 PAID
        this.paymentStatus = ClaimEnum.PaymentStatus.PROCESSING;
        this.phase = ClaimPhase.SETTLEMENT;
        this.updateTime = event.settledAt();
    }

    @EventSourcingHandler
    protected void on(DisabilityBenefitSettledEvent event) {
        this.disabilityEvidence = event.evidence();
        this.disabilityBenefitCalculation = event.benefitCalculation();
        this.settlement = event.settlement();
        // 全残给付结算后进入赔付中，保持 APPROVED 待支付域出账回写，不再直接置 PAID
        this.paymentStatus = ClaimEnum.PaymentStatus.PROCESSING;
        this.phase = ClaimPhase.SETTLEMENT;
        this.updateTime = event.settledAt();
    }

    @EventSourcingHandler
    protected void on(ClaimRejectedEvent event) {
        this.status = ClaimStatus.REJECTED;
        this.rejectionReason = event.reason();
        this.rejectedAt = event.rejectedAt();
        this.paymentStatus = ClaimEnum.PaymentStatus.REJECTED_CLOSED;
        // 拒赔是终态分支：阶段直接落到 REJECTED，与 SETTLEMENT/PAID 主链互斥
        this.phase = ClaimPhase.REJECTED;
        this.updateTime = event.rejectedAt();
    }

    @EventSourcingHandler
    protected void on(ClaimPaymentCompletedEvent event) {
        this.status = ClaimStatus.PAID;
        this.paymentStatus = ClaimEnum.PaymentStatus.SUCCESS;
        this.paymentNo = event.paymentNo();
        this.phase = ClaimPhase.PAID;
        this.updateTime = event.paidAt();
    }

    /**
     * 赔付失败回放：置赔付状态为 FAILED 并记录失败类型/原因/时间。
     * <p>
     * 注意**不动 {@code status}**（保持 APPROVED）——这是「可重派」的前提：
     * 若把案件状态也改成失败态，重派出款成功后的 {@code CompletePaymentCommand} 会因
     * {@code status != APPROVED} 被前置校验拒绝，赔案永久卡死。
     * </p>
     */
    @EventSourcingHandler
    protected void on(ClaimPaymentFailedEvent event) {
        this.paymentStatus = ClaimEnum.PaymentStatus.FAILED;
        this.paymentFailureType = event.failureType();
        this.paymentFailureReason = event.failureReason();
        this.paymentFailedAt = event.failedAt();
        this.updateTime = event.failedAt();
    }

    @EventSourcingHandler
    protected void on(ClaimClosedEvent event) {
        this.status = ClaimStatus.CLOSED;
        this.closedAt = event.closedAt();
        this.updateTime = event.closedAt();
    }
}
