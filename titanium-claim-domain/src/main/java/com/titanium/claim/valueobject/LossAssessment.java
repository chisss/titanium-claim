package com.titanium.claim.valueobject;

import java.math.BigDecimal;
import java.util.List;


/**
 * 定损记录值对象（车险按损定损阶段）
 * <p>
 * 记录理赔定损阶段的损失评估：定损总金额、定损明细项（损失项目/配件价格）、残值扣减、责任比例。
 * 车险按实际损失定损：赔付金额 = (定损总金额 − 残值扣减) × 责任比例。
 * </p>
 *
 * @param assessedAmount 定损总金额
 * @param items          定损明细项（维修项/损失项）
 * @param salvageValue   残值扣减（损余件残值，可为空=0，不超过定损总金额）
 * @param liabilityRatio 责任比例（**0-1 小数**，如全责1.0、同责0.5）
 * @param assessorId     定损员ID
 *
 * @author wei.sun
 * @since 2026/6/23
 */
public record LossAssessment(
        BigDecimal assessedAmount,
        List<LossItem> items,
        BigDecimal salvageValue,
        BigDecimal liabilityRatio,
        String assessorId) {

    public LossAssessment {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /**
     * 责任比例量纲不变量：契约口径为 0-1 小数（🔴 D-501-49）。
     * <p>
     * 越界值几乎只有一种来源 —— 调用方按百分数传入（80 表示 80%）。若放行，{@link #payableAmount()}
     * 会算出 <b>100 倍</b> 的核定赔付额，而该金额是核赔结算的唯一权威、会被支付域真实出账。
     * </p>
     * <p>
     * 🔴 <b>本校验刻意<i>不</i>放在紧凑构造器里</b>：本值对象是 {@code ClaimLossAssessedEvent} 的载荷字段，
     * 事件溯源重放历史事件时 Jackson 会走<b>同一个</b>规范构造器 —— 一旦构造即校验，
     * 事件库中<b>已存在</b>的越界值（修复前写入的 `80` / `100`）会让该聚合<b>永远无法加载</b>，
     * 报错还是序列化层的晦涩异常而非业务错误。<b>由 {@code Claim#handle(SubmitLossAssessmentCommand)}
     * 在命令入口校验</b>：新增不会再写进越界值，历史事件仍可重放。
     * </p>
     *
     * @param liabilityRatio 责任比例
     * @return 越界返回 {@code true}
     */
    public static boolean isOutOfDecimalScale(BigDecimal liabilityRatio) {
        return liabilityRatio != null
                && (liabilityRatio.compareTo(BigDecimal.ZERO) < 0
                        || liabilityRatio.compareTo(BigDecimal.ONE) > 0);
    }

    /**
     * 实际赔付金额 = (定损金额 − 残值扣减) × 责任比例
     *
     * @return 赔付金额
     */
    public BigDecimal payableAmount() {
        if (assessedAmount == null || liabilityRatio == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal salvage = salvageValue == null ? BigDecimal.ZERO : salvageValue;
        BigDecimal netLoss = assessedAmount.subtract(salvage);
        return netLoss.signum() <= 0 ? BigDecimal.ZERO : netLoss.multiply(liabilityRatio);
    }

    /**
     * 定损明细项
     *
     * @param itemName 项目名称（如保险杠/挡风玻璃）
     * @param amount   损失金额（配件价格）
     */
    public record LossItem(String itemName, BigDecimal amount) {
    }
}
