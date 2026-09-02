package com.titanium.claim.port.clause;

import java.util.List;

/**
 * 条款服务出口 Port（对端域：clause）
 * <p>
 * 领域需要的条款能力契约：保险责任校验（CLAIM-4 责任匹配）。责任按条款ID批量取回，
 * 由 application 编排翻译为领域值对象 {@code CoverageResult} 供领域服务纯规则判定。
 * 实现为 infrastructure 层 {@code adapter/clause/ClauseServiceAdapter}（调 ClauseApi Feign）。
 * </p>
 */
public interface ClauseServicePort {

    /**
     * 查询条款保险责任列表（Coverage），供领域服务判定出险是否在责任范围内。
     *
     * @param clauseId 条款ID（来自 policy 域保单条款快照 ClauseRef）
     * @param tenantId 租户ID
     * @return 保险责任摘要列表（无责任时返回空列表）
     */
    List<CoverageInfo> fetchCoverages(String clauseId, String tenantId);

    /**
     * 保险责任信息（ClauseServicePort 出参，领域视角的责任摘要）
     * <p>
     * 只承载责任判定要素（责任定位 + 等待期）；条款域按有效条款过滤返回，取到即视为有效责任。
     * 触发条件与赔付规则的完整结构由 application 按需扩展，本摘要保持轻量。
     * </p>
     *
     * @param coverageId        责任ID
     * @param coverageCode      责任编码
     * @param coverageName      责任名称
     * @param waitingPeriodDays 等待期天数（0/null 表示无等待期）
     */
    record CoverageInfo(
            String coverageId,
            String coverageCode,
            String coverageName,
            Integer waitingPeriodDays
    ) {
    }
}
