package com.titanium.claim.port.policy;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 保单信息（PolicyServicePort 出参，领域视角的保单摘要）
 *
 * @param policyId        保单ID
 * @param statusCode      保单状态码（对端域原生状态码，policy 域有效状态为 EFFECTIVE）
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

    /**
     * 保单是否有效（可报案）。
     * <p>
     * policy 域原生有效状态码为 {@code EFFECTIVE}（非 ACTIVE，早期按 ACTIVE 硬编码比对曾致
     * 报案校验恒失败）。对端状态语义内聚于本防腐值对象，调用方不感知状态码差异。
     * </p>
     */
    // 🔴 @JsonIgnore 不可删（D-501-19，与 D-501-18 investment/regulatory 同病）：
    //    派生 getter 非 record 分量，却会被 Axon 的 Jackson 序列化器写进事件/载荷
    //    （isXxx → 属性 "effective"），重放时报 UnrecognizedPropertyException → 聚合无法加载。
    @JsonIgnore
    public boolean isEffective() {
        return "EFFECTIVE".equals(statusCode);
    }
}
