package com.titanium.claim.event;

import java.time.LocalDateTime;

import org.axonframework.serialization.Revision;

import com.titanium.claim.common.enums.ClaimStatus;
import com.titanium.claim.valueobject.ClaimId;

@Revision("1")
public record ClaimStatusChangedEvent(
        ClaimId claimId,
        ClaimStatus oldStatus,
        ClaimStatus newStatus,
        String reason,
        LocalDateTime changedAt,
        String tenantId
) {
}
