package com.titanium.claim.port.clause;

/**
 * 条款服务出口 Port（对端域：clause）
 * <p>
 * 领域需要的条款能力契约：保险责任校验（CLAIM-4 责任匹配）。
 * 实现为 infrastructure 层 {@code adapter/clause/ClauseServiceAdapter}（M2 接 clause 域 Feign）。
 * </p>
 */
public interface ClauseServicePort {

    /**
     * 查询保险责任（Coverage），供领域服务判定出险是否在责任范围内。
     *
     * @param clauseCode 条款编码
     * @param tenantId   租户ID
     * @return 保险责任信息（不存在时返回 null，由调用方判定）
     */
    CoverageInfo fetchCoverage(String clauseCode, String tenantId);

    /**
     * 保险责任信息（ClauseServicePort 出参，领域视角的责任摘要）
     *
     * @param clauseCode  条款编码
     * @param coverageId  责任ID
     * @param coverageName 责任名称
     * @param effective   责任是否有效
     */
    record CoverageInfo(
            String clauseCode,
            String coverageId,
            String coverageName,
            boolean effective
    ) {
    }
}
