package com.titanium.claim.event;

import java.time.LocalDateTime;

import org.axonframework.serialization.Revision;

import com.titanium.claim.valueobject.ClaimAmount;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.metadata.enums.claim.ClaimEnum;

@Revision("1")
public record ClaimUpdatedEvent(
        ClaimId claimId,
        ClaimEnum.ClaimType claimType,
        LocalDateTime incidentDate,
        String incidentDescription,
        ClaimAmount claimAmount,
        LocalDateTime updatedAt,
        String tenantId
) {
}
