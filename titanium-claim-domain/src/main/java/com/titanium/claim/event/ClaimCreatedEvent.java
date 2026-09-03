package com.titanium.claim.event;

import java.time.LocalDateTime;

import com.titanium.claim.valueobject.ClaimAmount;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.CustomerId;
import com.titanium.claim.valueobject.PolicyId;
import com.titanium.metadata.enums.claim.ClaimEnum;

/**
 * 理赔创建事件
 * <p>
 * {@code tenantId} 随事件流携带，供读模型投影落库（{@code t_claim_view.tenant_id} 非空约束）。
 * 历史事件（该字段引入前产生）反序列化后为 null，投影侧回落默认租户兜底。
 * </p>
 */
public record ClaimCreatedEvent(
        ClaimId claimId,
        CustomerId customerId,
        PolicyId policyId,
        String claimNumber,
        ClaimEnum.ClaimType claimType,
        LocalDateTime incidentDate,
        String incidentDescription,
        ClaimAmount claimAmount,
        LocalDateTime createdAt,
        String tenantId
) {
}
