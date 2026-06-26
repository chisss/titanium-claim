package com.titanium.claim.command;

import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.CustomerId;
import com.titanium.claim.valueobject.PolicyId;
import com.titanium.claim.valueobject.ClaimAmount;
import com.titanium.metadata.enums.claim.ClaimEnum;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.time.LocalDateTime;

/**
 * 创建理赔命令（领域层）
 */
public record CreateClaimCommand(
        @TargetAggregateIdentifier ClaimId claimId,
        CustomerId customerId,
        PolicyId policyId,
        String claimNumber,
        ClaimEnum.ClaimType claimType,
        LocalDateTime incidentDate,
        String incidentDescription,
        ClaimAmount claimAmount
) {
}
