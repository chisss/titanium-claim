package com.titanium.claim.query;

import com.titanium.claim.enums.ClaimStatus;

/**
 * 根据状态查询理赔案件列表
 * <p>
 * 用于查询指定状态的所有理赔案件
 * </p>
 */
public record FindClaimsByStatusQuery(
        ClaimStatus status,
        String tenantId,
        int page,
        int size
) {
}
