package com.titanium.claim.port.policy;

import java.math.BigDecimal;

/**
 * 保单信息（PolicyServicePort 出参，领域视角的保单摘要）
 *
 * @param policyId        保单ID
 * @param statusCode      保单状态码（对端域原生状态码，如 ACTIVE）
 * @param basicSumInsured 基本保额（定额给付精算依据，如身故/全残给付）
 */
public record PolicyInfo(
        String policyId,
        String statusCode,
        BigDecimal basicSumInsured
) {
}
