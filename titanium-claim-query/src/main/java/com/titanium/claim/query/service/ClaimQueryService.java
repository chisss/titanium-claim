package com.titanium.claim.query.service;

import java.util.List;
import java.util.Optional;

import com.titanium.claim.query.result.ClaimQueryResult;

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
    Optional<ClaimQueryResult> getClaimSummary(String claimId);

    /**
     * 根据客户ID查询理赔案件摘要列表
     */
    List<ClaimQueryResult> getClaimSummariesByCustomerId(String customerId);

    /**
     * 根据保单ID查询理赔案件摘要列表
     */
    List<ClaimQueryResult> getClaimSummariesByPolicyId(String policyId);

    /**
     * 根据状态查询理赔案件摘要列表
     */
    List<ClaimQueryResult> getClaimSummariesByStatus(String status);

    /**
     * 查询全部理赔案件摘要列表
     */
    List<ClaimQueryResult> getAllClaimSummaries();
}
