package com.titanium.claim.event;

import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.ClaimAmount;
import com.titanium.metadata.enums.claim.ClaimEnum;

import java.time.LocalDateTime;

public record ClaimUpdatedEvent(
        ClaimId claimId,
        ClaimEnum.ClaimType claimType,
        LocalDateTime incidentDate,
        String incidentDescription,
        ClaimAmount claimAmount,
        LocalDateTime updatedAt
) {
}