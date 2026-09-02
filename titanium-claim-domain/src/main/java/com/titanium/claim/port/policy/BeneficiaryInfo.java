package com.titanium.claim.port.policy;

import java.math.BigDecimal;

/**
 * 保单受益人摘要（PolicyServicePort 出参，领域视角的受益人主数据镜像）
 * <p>
 * 身故给付受益人核验（CLAIM-4）的比对基准：请求受益人必须在保单受益人主数据中，
 * 给付分配按 {@code orderNo} 顺位排序（第一顺位优先），拒绝未知受益人。
 * </p>
 *
 * @param beneficiaryId   受益人ID
 * @param beneficiaryName 受益人姓名
 * @param beneficiaryType 受益人类型（法定/指定）
 * @param orderNo         受益顺位（1 起，第一顺位优先）
 * @param shareRatio      登记份额（百分比数值，如 50 表示 50%）
 */
public record BeneficiaryInfo(
        String beneficiaryId,
        String beneficiaryName,
        String beneficiaryType,
        Integer orderNo,
        BigDecimal shareRatio
) {
}
