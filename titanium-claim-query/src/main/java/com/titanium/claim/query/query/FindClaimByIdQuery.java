package com.titanium.claim.query.query;

/**
 * 根据ID查询理赔案件（CQRS 读侧查询入参）
 *
 * @param claimId 理赔案件ID
 */
public record FindClaimByIdQuery(String claimId) {
}
