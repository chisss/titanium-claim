package com.titanium.claim.query;

import com.titanium.claim.valueobject.ClaimId;

/**
 * 理赔案件查询类
 * <p>
 * 用于查询理赔案件的详细信息
 * </p>
 */
public record ClaimQuery(
        ClaimId claimId,
        String tenantId
) {
}