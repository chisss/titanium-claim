package com.titanium.claim.api.response;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 理赔统计远程响应契约（Feign 出参）
 * <p>
 * 面向管理后台等跨服务消费者的对外传输契约，承载理赔维度聚合统计：待处理理赔数、今日报案数、
 * 理赔总数及累计已结案赔付金额。由 web 层经 MapStruct 从读侧 {@code ClaimStatisticsResult}
 * 转换而来，作为稳定协议隔离读模型内部结构。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClaimStatisticsResponse {

    /** 待处理理赔数（未结案状态案件数） */
    private long pendingClaimCount;

    /** 今日报案数（create_time 落在当天） */
    private long todayClaimCount;

    /** 理赔总数 */
    private long totalClaimCount;

    /** 累计已结案赔付金额（已支付案件 settled_amount 之和，无数据为 0） */
    private BigDecimal totalSettledAmount;
}
