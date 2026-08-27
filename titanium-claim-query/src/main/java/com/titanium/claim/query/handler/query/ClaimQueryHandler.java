package com.titanium.claim.query.handler.query;

import java.util.List;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import com.titanium.claim.query.query.FindClaimByIdQuery;
import com.titanium.claim.query.query.FindClaimsByCustomerIdQuery;
import com.titanium.claim.query.query.FindClaimsByPolicyIdQuery;
import com.titanium.claim.query.query.FindClaimsByStatusQuery;
import com.titanium.claim.query.result.ClaimQueryResult;
import com.titanium.claim.query.service.ClaimQueryService;

import lombok.RequiredArgsConstructor;

/**
 * 理赔查询处理器（CQRS 读侧 Axon 查询处理）
 * <p>
 * 接收 {@code FindXxxQuery}，委托 {@link ClaimQueryService} 查询读模型并返回 DTO（不存在时返回 {@code null}）。
 * </p>
 */
@Component
@RequiredArgsConstructor
@ProcessingGroup("claim-query-group")
public class ClaimQueryHandler {

    private final ClaimQueryService claimQueryService;

    @QueryHandler
    public ClaimQueryResult handle(FindClaimByIdQuery query) {
        return claimQueryService.getClaimSummary(query.claimId(), query.tenantId()).orElse(null);
    }

    @QueryHandler
    public List<ClaimQueryResult> handle(FindClaimsByCustomerIdQuery query) {
        return claimQueryService.getClaimSummariesByCustomerId(query.customerId(), query.tenantId());
    }

    @QueryHandler
    public List<ClaimQueryResult> handle(FindClaimsByPolicyIdQuery query) {
        return claimQueryService.getClaimSummariesByPolicyId(query.policyId(), query.tenantId());
    }

    @QueryHandler
    public List<ClaimQueryResult> handle(FindClaimsByStatusQuery query) {
        return claimQueryService.getClaimSummariesByStatus(query.status(), query.tenantId());
    }
}
