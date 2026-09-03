package com.titanium.claim.command;

import java.time.LocalDateTime;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.claim.valueobject.ClaimAmount;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.CustomerId;
import com.titanium.claim.valueobject.PolicyId;
import com.titanium.metadata.enums.claim.ClaimEnum;

/**
 * 创建理赔命令（领域层）
 * <p>
 * {@code tenantId} 贯穿事件（{@link com.titanium.claim.event.ClaimCreatedEvent} 携带），
 * 供读模型投影落库（{@code t_claim_view.tenant_id} 非空约束），多租户隔离贯穿全链路。
 * </p>
 */
public record CreateClaimCommand(
        @TargetAggregateIdentifier ClaimId claimId,
        CustomerId customerId,
        PolicyId policyId,
        String claimNumber,
        ClaimEnum.ClaimType claimType,
        LocalDateTime incidentDate,
        String incidentDescription,
        ClaimAmount claimAmount,
        String tenantId
) {
}
