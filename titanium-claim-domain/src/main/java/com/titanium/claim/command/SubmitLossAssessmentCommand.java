package com.titanium.claim.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.LossAssessment;

/**
 * 提交定损命令（推进理赔阶段 SURVEY → LOSS_ASSESS）
 */
public record SubmitLossAssessmentCommand(
        @TargetAggregateIdentifier ClaimId claimId,
        LossAssessment lossAssessment) {
}
