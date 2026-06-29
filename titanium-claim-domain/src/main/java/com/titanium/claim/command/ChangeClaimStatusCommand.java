package com.titanium.claim.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.claim.enums.ClaimStatus;
import com.titanium.claim.valueobject.ClaimId;

/**
 * 变更理赔状态命令（领域层）
 */
public record ChangeClaimStatusCommand(
        @TargetAggregateIdentifier ClaimId claimId,
        ClaimStatus newStatus,
        String reason
) {
}
