package com.titanium.claim.query.query;

/**
 * 根据保单ID查询理赔案件列表（CQRS 读侧查询入参）
 *
 * @param policyId 保单ID
 */
public record FindClaimsByPolicyIdQuery(String policyId) {
}
