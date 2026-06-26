package com.titanium.claim.event;

import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.Survey;
import com.titanium.metadata.enums.claim.ClaimPhase;

import java.time.LocalDateTime;

/**
 * 查勘已提交事件（理赔阶段推进至 SURVEY）
 */
public record ClaimSurveySubmittedEvent(
        ClaimId claimId,
        Survey survey,
        ClaimPhase newPhase,
        LocalDateTime submittedAt) {
}
