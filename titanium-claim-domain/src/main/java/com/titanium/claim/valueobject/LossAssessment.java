package com.titanium.claim.valueobject;

import java.math.BigDecimal;
import java.util.List;

/**
 * 定损记录值对象（车险按损定损阶段）
 * <p>
 * 记录理赔定损阶段的损失评估：定损总金额、定损明细项、责任比例。 车险按实际损失定损，乘以责任比例得出赔付金额。
 * </p>
 *
 * @param assessedAmount 定损总金额
 * @param items          定损明细项（维修项/损失项）
 * @param liabilityRatio 责任比例（0-1，如全责1.0、同责0.5）
 * @param assessorId     定损员ID
 *
 * @author wei.sun
 * @since 2026/6/23
 */
public record LossAssessment(
        BigDecimal assessedAmount,
        List<LossItem> items,
        BigDecimal liabilityRatio,
        String assessorId) {

    public LossAssessment {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /**
     * 实际赔付金额 = 定损金额 × 责任比例
     *
     * @return 赔付金额
     */
    public BigDecimal payableAmount() {
        if (assessedAmount == null || liabilityRatio == null) {
            return BigDecimal.ZERO;
        }
        return assessedAmount.multiply(liabilityRatio);
    }

    /**
     * 定损明细项
     *
     * @param itemName 项目名称（如保险杠/挡风玻璃）
     * @param amount   损失金额
     */
    public record LossItem(String itemName, BigDecimal amount) {
    }
}
