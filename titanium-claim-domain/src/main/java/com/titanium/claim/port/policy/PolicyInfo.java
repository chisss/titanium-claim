package com.titanium.claim.port.policy;

/**
 * 保单信息（PolicyServicePort 出参，领域视角的保单摘要）
 *
 * @param policyId   保单ID
 * @param statusCode 保单状态码（对端域原生状态码，如 ACTIVE）
 */
public record PolicyInfo(
        String policyId,
        String statusCode
) {
}
