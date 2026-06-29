package com.titanium.claim.event;

import java.time.LocalDateTime;

import com.titanium.claim.valueobject.ClaimAmount;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.CustomerId;
import com.titanium.claim.valueobject.PolicyId;
import com.titanium.metadata.enums.claim.ClaimEnum;

public record ClaimCreatedEvent(
        ClaimId claimId,
        CustomerId customerId,
        PolicyId policyId,
        String claimNumber,
        ClaimEnum.ClaimType claimType,
        LocalDateTime incidentDate,
        String incidentDescription,
        ClaimAmount claimAmount,
        LocalDateTime createdAt
) {
}
