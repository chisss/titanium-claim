package com.titanium.claim.event;

import java.time.LocalDateTime;

import org.axonframework.serialization.Revision;

import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.Survey;
import com.titanium.metadata.enums.claim.ClaimPhase;

/**
 * 查勘已提交事件（理赔阶段推进至 SURVEY）
 */
@Revision("1")
public record ClaimSurveySubmittedEvent(
        ClaimId claimId,
        Survey survey,
        ClaimPhase newPhase,
        LocalDateTime submittedAt) {
}
