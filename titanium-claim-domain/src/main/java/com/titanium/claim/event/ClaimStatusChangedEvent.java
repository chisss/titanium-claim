package com.titanium.claim.event;

import java.time.LocalDateTime;

import com.titanium.claim.enums.ClaimStatus;
import com.titanium.claim.valueobject.ClaimId;

public record ClaimStatusChangedEvent(
        ClaimId claimId,
        ClaimStatus oldStatus,
        ClaimStatus newStatus,
        String reason,
        LocalDateTime changedAt
) {
}
