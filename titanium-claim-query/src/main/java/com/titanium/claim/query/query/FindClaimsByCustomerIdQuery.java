package com.titanium.claim.query.query;

/**
 * 根据客户ID查询理赔案件列表（CQRS 读侧查询入参）
 *
 * @param customerId 客户ID
 */
public record FindClaimsByCustomerIdQuery(String customerId, String tenantId) {
}
