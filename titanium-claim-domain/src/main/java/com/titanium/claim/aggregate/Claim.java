package com.titanium.claim.aggregate;

import java.time.LocalDateTime;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

import com.titanium.claim.command.ChangeClaimStatusCommand;
import com.titanium.claim.command.CreateClaimCommand;
import com.titanium.claim.command.SettleClaimCommand;
import com.titanium.claim.command.SubmitLossAssessmentCommand;
import com.titanium.claim.command.SubmitSurveyCommand;
import com.titanium.claim.command.UpdateClaimCommand;
import com.titanium.claim.enums.ClaimStatus;
import com.titanium.claim.event.ClaimCreatedEvent;
import com.titanium.claim.event.ClaimLossAssessedEvent;
import com.titanium.claim.event.ClaimSettledEvent;
import com.titanium.claim.event.ClaimStatusChangedEvent;
import com.titanium.claim.event.ClaimSurveySubmittedEvent;
import com.titanium.claim.event.ClaimUpdatedEvent;
import com.titanium.claim.exception.ClaimPhaseTransitionException;
import com.titanium.claim.exception.ClaimStatusPreconditionException;
import com.titanium.claim.exception.ClaimStatusTransitionException;
import com.titanium.claim.valueobject.ClaimAmount;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.ClaimSettlement;
import com.titanium.claim.valueobject.CustomerId;
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
 * 管理理赔案件全生命周期：报案(PENDING) → 处理(PROCESSING) → 核赔通过(APPROVED) → 赔付(PAID)，
 * 任意阶段可拒赔(REJECTED)。核赔结算(settle)记录赔付结论并流转至 PAID。
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
    /** 理赔处理阶段（报案→查勘→定损→核赔→结算→赔付） */
    private ClaimPhase          phase;
    /** 查勘记录（车险/调查类案件） */
    private Survey              survey;
    /** 定损记录（车险按损案件） */
    private LossAssessment      lossAssessment;

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
        // 状态机合法性校验：禁止非法跳转（PAID/REJECTED 为终态）
        validateTransition(status, command.newStatus());
        AggregateLifecycle.apply(new ClaimStatusChangedEvent(command.claimId(), status, command.newStatus(),
                command.reason(), LocalDateTime.now()));
    }

    /**
     * 核赔结算：仅 APPROVED 状态可结算，记录赔付结论并流转至 PAID。
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
     * 理赔状态流转合法性校验。
     * <p>
     * 合法流转：PENDING→PROCESSING/REJECTED；PROCESSING→APPROVED/REJECTED；APPROVED→PAID。
     * PAID/REJECTED 为终态，不可再流转。
     * </p>
     */
    private void validateTransition(ClaimStatus from, ClaimStatus to) {
        boolean legal = switch (from) {
            case PENDING -> to == ClaimStatus.PROCESSING || to == ClaimStatus.REJECTED;
            case PROCESSING -> to == ClaimStatus.APPROVED || to == ClaimStatus.REJECTED;
            case APPROVED -> to == ClaimStatus.PAID;
            case PAID, REJECTED -> false;
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
        this.status = ClaimStatus.PAID;
        this.updateTime = event.settledAt();
    }
}
