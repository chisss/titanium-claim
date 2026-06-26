package com.titanium.claim.event;

import com.titanium.claim.enums.ClaimStatus;
import com.titanium.claim.valueobject.ClaimId;

import java.time.LocalDateTime;

public record ClaimStatusChangedEvent(
        ClaimId claimId,
        ClaimStatus oldStatus,
        ClaimStatus newStatus,
        String reason,
        LocalDateTime changedAt
) {
}