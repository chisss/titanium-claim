package com.titanium.claim.port.policy;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 保单信息（PolicyServicePort 出参，领域视角的保单摘要）
 *
 * @param policyId        保单ID
 * @param statusCode      保单状态码（对端域原生状态码，如 ACTIVE）
 * @param basicSumInsured 基本保额（定额给付精算依据，如身故/全残给付）
 * @param cashValue       账户价值（可空：policy 域暂未提供账户价值查询接口，为空时全残给付回落基本保额）
 * @param effectiveDate   生效日期（等待期起算基准，责任校验 CLAIM-4 用）
 */
public record PolicyInfo(
        String policyId,
        String statusCode,
        BigDecimal basicSumInsured,
        BigDecimal cashValue,
        LocalDateTime effectiveDate
) {
}
