package com.titanium.claim.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.Survey;

/**
 * 提交查勘命令（推进理赔阶段 REPORT/PROCESSING → SURVEY）
 */
public record SubmitSurveyCommand(
        @TargetAggregateIdentifier ClaimId claimId,
        Survey survey) {
}
