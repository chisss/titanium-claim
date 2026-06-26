package com.titanium.claim.command;

import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.ClaimAmount;
import com.titanium.metadata.enums.claim.ClaimEnum;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.time.LocalDateTime;

/**
 * 更新理赔命令（领域层）
 */
public record UpdateClaimCommand(
        @TargetAggregateIdentifier ClaimId claimId,
        ClaimEnum.ClaimType claimType,
        LocalDateTime incidentDate,
        String incidentDescription,
        ClaimAmount claimAmount
) {
}
