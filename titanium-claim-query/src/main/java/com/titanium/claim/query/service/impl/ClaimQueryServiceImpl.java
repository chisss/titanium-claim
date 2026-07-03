package com.titanium.claim.query.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.claim.common.enums.ClaimStatus;
import com.titanium.claim.query.repository.ClaimViewRepository;
import com.titanium.claim.query.result.ClaimQueryResult;
import com.titanium.claim.query.service.ClaimQueryService;
import com.titanium.claim.query.view.ClaimView;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 理赔查询服务实现（CQRS 读侧）
 * <p>
 * 查询读模型表 {@code t_claim_view}（由 {@code ClaimProjectionEventHandler} 投影维护），
 * 组装为稳定 DTO 返回，禁止直接返回读模型实体。
 * </p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ClaimQueryServiceImpl implements ClaimQueryService {

    private final ClaimViewRepository claimViewRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<ClaimQueryResult> getClaimSummary(String claimId) {
        return claimViewRepository.findByClaimId(claimId).map(this::toResult);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClaimQueryResult> getClaimSummariesByCustomerId(String customerId) {
        return claimViewRepository.findByCustomerId(customerId).stream().map(this::toResult)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClaimQueryResult> getClaimSummariesByPolicyId(String policyId) {
        return claimViewRepository.findByPolicyId(policyId).stream().map(this::toResult)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClaimQueryResult> getClaimSummariesByStatus(String status) {
        return claimViewRepository.findByStatus(ClaimStatus.fromCode(status)).stream().map(this::toResult)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClaimQueryResult> getAllClaimSummaries() {
        return claimViewRepository.findAll().stream().map(this::toResult).collect(Collectors.toList());
    }

    // ==================== 转换方法：读模型 → DTO ====================

    private ClaimQueryResult toResult(ClaimView view) {
        ClaimQueryResult result = new ClaimQueryResult();
        result.setClaimId(view.getClaimId());
        result.setCustomerId(view.getCustomerId());
        result.setPolicyId(view.getPolicyId());
        result.setClaimNumber(view.getClaimNumber());
        result.setClaimType(view.getClaimType());
        result.setIncidentDate(view.getIncidentDate());
        result.setIncidentDescription(view.getIncidentDescription());
        result.setClaimAmount(view.getClaimAmount());
        result.setStatus(view.getStatus());
        result.setPhase(view.getPhase());
        result.setSettledAmount(view.getSettledAmount());
        result.setCreatedAt(view.getCreateTime());
        result.setUpdatedAt(view.getUpdateTime());
        return result;
    }
}
