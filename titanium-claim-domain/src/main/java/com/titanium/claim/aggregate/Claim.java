package com.titanium.claim.aggregate;

import java.time.LocalDateTime;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

import com.titanium.claim.command.ChangeClaimStatusCommand;
import com.titanium.claim.command.CloseClaimCommand;
import com.titanium.claim.command.CompletePaymentCommand;
import com.titanium.claim.command.CreateClaimCommand;
import com.titanium.claim.command.RejectClaimCommand;
import com.titanium.claim.command.SettleClaimCommand;
import com.titanium.claim.command.SettleDeathBenefitCommand;
import com.titanium.claim.command.SubmitLossAssessmentCommand;
import com.titanium.claim.command.SubmitSurveyCommand;
import com.titanium.claim.command.UpdateClaimCommand;
import com.titanium.claim.common.enums.ClaimStatus;
import com.titanium.claim.common.enums.RejectReason;
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
import com.titanium.claim.exception.ClaimPhaseTransitionException;
import com.titanium.claim.exception.ClaimStatusPreconditionException;
import com.titanium.claim.exception.ClaimStatusTransitionException;
import com.titanium.claim.valueobject.BenefitCalculation;
import com.titanium.claim.valueobject.ClaimAmount;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.ClaimSettlement;
import com.titanium.claim.valueobject.CustomerId;
import com.titanium.claim.valueobject.DeathClaimEvidence;
import com.titanium.claim.valueobject.LossAssessment;
import com.titanium.claim.valueobject.PolicyId;
import com.titanium.claim.valueobject.Survey;
import com.titanium.common.domain.BaseAggregate;
import com.titanium.metadata.enums.claim.ClaimEnum;
import com.titanium.metadata.enums.claim.ClaimPhase;

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

    @CommandHandler
    public Claim(CreateClaimCommand command) {
        AggregateLifecycle.apply(new ClaimCreatedEvent(command.claimId(), command.customerId(), command.policyId(),
                command.claimNumber(), command.claimType(), command.incidentDate(), command.incidentDescription(),
                command.claimAmount(), LocalDateTime.now()));
    }

    @CommandHandler
    public void handle(UpdateClaimCommand command) {
        AggregateLifecycle.apply(new ClaimUpdatedEvent(command.claimId(), command.claimType(), command.incidentDate(),
                command.incidentDescription(), command.claimAmount(), LocalDateTime.now()));
    }

    @CommandHandler
    public void handle(ChangeClaimStatusCommand command) {
        if (status == null || status == command.newStatus()) {
            return;
        }
        // 状态机合法性校验：禁止非法跳转（拒赔/赔付/结案走专用命令，不经此通用通道）
        validateTransition(status, command.newStatus());
        AggregateLifecycle.apply(new ClaimStatusChangedEvent(command.claimId(), status, command.newStatus(),
                command.reason(), LocalDateTime.now()));
    }

    /**
     * 核赔结算：仅 APPROVED 状态可结算，记录赔付结论并进入赔付中，
     * 待支付域出账成功回写 {@link CompletePaymentCommand} 后流转至 PAID。
     */
    @CommandHandler
    public void handle(SettleClaimCommand command) {
        if (status != ClaimStatus.APPROVED) {
            throw new ClaimStatusPreconditionException(command.claimId(), status, "核赔结算", "APPROVED");
        }
        ClaimSettlement claimSettlement = ClaimSettlement.of(command.settledAmount(), command.payoutMethod(),
                command.payeeAccount(), command.conclusion());
        AggregateLifecycle.apply(new ClaimSettledEvent(command.claimId(), claimSettlement, LocalDateTime.now()));
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
                command.evidence(), command.benefitCalculation(), deathSettlement, LocalDateTime.now()));
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
        AggregateLifecycle
                .apply(new ClaimRejectedEvent(command.claimId(), command.reason(), command.comment(), LocalDateTime.now()));
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
    protected void on(ClaimStatusChangedEvent event) {
        this.status = event.newStatus();
        this.updateTime = event.changedAt();
    }

    @EventSourcingHandler
    protected void on(ClaimSettledEvent event) {
        this.settlement = event.settlement();
        // 结算后进入赔付中，保持 APPROVED 待支付域出账回写，不再直接置 PAID
        this.paymentStatus = ClaimEnum.PaymentStatus.PROCESSING;
        this.updateTime = event.settledAt();
    }

    @EventSourcingHandler
    protected void on(DeathBenefitSettledEvent event) {
        this.deathEvidence = event.evidence();
        this.benefitCalculation = event.benefitCalculation();
        this.settlement = event.settlement();
        // 身故给付结算后进入赔付中，保持 APPROVED 待支付域出账回写，不再直接置 PAID
        this.paymentStatus = ClaimEnum.PaymentStatus.PROCESSING;
        this.updateTime = event.settledAt();
    }

    @EventSourcingHandler
    protected void on(ClaimRejectedEvent event) {
        this.status = ClaimStatus.REJECTED;
        this.rejectionReason = event.reason();
        this.rejectedAt = event.rejectedAt();
        this.paymentStatus = ClaimEnum.PaymentStatus.REJECTED_CLOSED;
        this.updateTime = event.rejectedAt();
    }

    @EventSourcingHandler
    protected void on(ClaimPaymentCompletedEvent event) {
        this.status = ClaimStatus.PAID;
        this.paymentStatus = ClaimEnum.PaymentStatus.SUCCESS;
        this.paymentNo = event.paymentNo();
        this.updateTime = event.paidAt();
    }

    @EventSourcingHandler
    protected void on(ClaimClosedEvent event) {
        this.status = ClaimStatus.CLOSED;
        this.closedAt = event.closedAt();
        this.updateTime = event.closedAt();
    }
}
