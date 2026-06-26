package com.titanium.claim.command;

import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.Survey;

/**
 * 提交查勘命令（推进理赔阶段 REPORT/PROCESSING → SURVEY）
 */
public record SubmitSurveyCommand(
        ClaimId claimId,
        Survey survey) {
}
