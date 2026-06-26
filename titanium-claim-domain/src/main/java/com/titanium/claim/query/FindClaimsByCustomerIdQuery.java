package com.titanium.claim.query;

import com.titanium.claim.valueobject.CustomerId;

/**
 * 根据客户ID查询理赔案件列表
 * <p>
 * 用于查询指定客户的所有理赔案件
 * </p>
 */
public record FindClaimsByCustomerIdQuery(
        CustomerId customerId,
        String tenantId,
        int page,
        int size
) {
}