package com.titanium.claim.query;

import com.titanium.claim.valueobject.PolicyId;

/**
 * 根据保单ID查询理赔案件列表
 * <p>
 * 用于查询指定保单的所有理赔案件
 * </p>
 */
public record FindClaimsByPolicyIdQuery(
        PolicyId policyId,
        String tenantId,
        int page,
        int size
) {
}