package com.titanium.claim.query.service;

import java.util.List;
import java.util.Optional;

import com.titanium.claim.query.result.ClaimQueryResult;
import com.titanium.claim.query.result.ClaimStatisticsResult;

/**
 * 理赔查询服务（CQRS 读侧）
 * <p>
 * 查询由事件投影维护的读模型表 {@code t_claim_view}，返回稳定 DTO 契约。
 * </p>
 */
public interface ClaimQueryService {

    /**
     * 根据理赔案件ID查询摘要
     */
    Optional<ClaimQueryResult> getClaimSummary(String claimId, String tenantId);

    /**
     * 根据客户ID查询理赔案件摘要列表
     */
    List<ClaimQueryResult> getClaimSummariesByCustomerId(String customerId, String tenantId);

    /**
     * 根据保单ID查询理赔案件摘要列表
     */
    List<ClaimQueryResult> getClaimSummariesByPolicyId(String policyId, String tenantId);

    /**
     * 根据状态查询理赔案件摘要列表
     */
    List<ClaimQueryResult> getClaimSummariesByStatus(String status, String tenantId);

    /**
     * 查询全部理赔案件摘要列表
     */
    List<ClaimQueryResult> getAllClaimSummaries(String tenantId);

    /**
     * 查询理赔聚合统计（管理后台看板用）
     * <p>
     * 聚合读模型表 {@code t_claim_view}：待处理理赔数、今日报案数、理赔总数，及累计已结案赔付金额。
     * 强制携带 {@code tenantId} 保证多租户隔离。
     * </p>
     *
     * @param tenantId 租户ID
     * @return 理赔统计结果
     */
    ClaimStatisticsResult getStatistics(String tenantId);
}
