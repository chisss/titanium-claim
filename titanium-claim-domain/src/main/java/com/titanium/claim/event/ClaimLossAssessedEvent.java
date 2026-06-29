package com.titanium.claim.event;

import java.time.LocalDateTime;

import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.LossAssessment;
import com.titanium.metadata.enums.claim.ClaimPhase;

/**
 * 定损已提交事件（理赔阶段推进至 LOSS_ASSESS）
 */
public record ClaimLossAssessedEvent(
        ClaimId claimId,
        LossAssessment lossAssessment,
        ClaimPhase newPhase,
        LocalDateTime assessedAt) {
}
