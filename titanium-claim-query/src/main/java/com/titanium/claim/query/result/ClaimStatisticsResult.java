package com.titanium.claim.query.result;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 理赔统计查询结果（管理后台看板聚合用）
 * <p>
 * 承载理赔维度的聚合统计：待处理理赔数、今日报案数、理赔总数，及累计已结案赔付金额。
 * 数据来源于读模型表 {@code t_claim_view}，属最终一致视图，仅供展示，不用于业务强一致决策。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClaimStatisticsResult {

    /** 待处理理赔数（状态为 PENDING/PROCESSING/APPROVED 等未结案状态） */
    private long       pendingClaimCount;

    /** 今日报案数（create_time 落在当天） */
    private long       todayClaimCount;

    /** 理赔总数 */
    private long       totalClaimCount;

    /** 累计已结案赔付金额（已支付案件 settled_amount 之和，无数据为 0） */
    private BigDecimal totalSettledAmount;
}
