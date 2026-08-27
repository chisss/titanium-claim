package com.titanium.claim.query.query;

/**
 * 根据状态查询理赔案件列表（CQRS 读侧查询入参）
 *
 * @param status 理赔状态 code
 */
public record FindClaimsByStatusQuery(String status, String tenantId) {
}
