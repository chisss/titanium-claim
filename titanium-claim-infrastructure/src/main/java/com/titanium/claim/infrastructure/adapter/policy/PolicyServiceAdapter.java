package com.titanium.claim.infrastructure.adapter.policy;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import com.titanium.claim.common.exception.BusinessException;
import com.titanium.claim.port.policy.BeneficiaryInfo;
import com.titanium.claim.port.policy.ClauseRef;
import com.titanium.claim.port.policy.PolicyInfo;
import com.titanium.claim.port.policy.PolicyServicePort;
import com.titanium.metadata.errorcode.ClaimErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.PolicyApi;
import com.titanium.policy.api.response.PolicyBeneficiaryResponse;
import com.titanium.policy.api.response.PolicyClauseResponse;
import com.titanium.policy.api.response.PolicyResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单服务 Adapter（对端域：policy）
 * <p>
 * 实现 {@link PolicyServicePort}：调用保单域 {@link PolicyApi} Feign，将下游契约
 * {@link PolicyResponse}/{@link PolicyClauseResponse}/{@link PolicyBeneficiaryResponse} 翻译为
 * 领域摘要 {@link PolicyInfo}/{@link ClauseRef}/{@link BeneficiaryInfo}（防腐：领域不依赖对端 api 类型）。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyServiceAdapter implements PolicyServicePort {

    private final PolicyApi policyApi;

    @Override
    public PolicyInfo getPolicy(String policyId, String tenantId) {
        log.info("获取保单详情, policyId={}, tenantId={}", policyId, tenantId);
        ApiResponse<PolicyResponse> response = policyApi.getPolicy(policyId, tenantId);
        if (!response.isSuccess()) {
            log.error("获取保单详情失败, policyId={}, error={}", policyId, response.getMessage());
            throw new BusinessException(ClaimErrorCode.POLICY_DETAIL_QUERY_FAILED,
                    "获取保单详情失败: " + response.getMessage());
        }
        PolicyResponse policy = response.getData();
        if (policy == null) {
            return null;
        }
        return new PolicyInfo(policy.getPolicyId(),
                policy.getStatus() == null ? null : policy.getStatus().getCode(),
                policy.getSumInsured() == null ? null : BigDecimal.valueOf(policy.getSumInsured()),
                policy.getEffectiveDate());
    }

    @Override
    public List<ClauseRef> fetchClauses(String policyId, String tenantId) {
        log.info("查询保单条款快照, policyId={}, tenantId={}", policyId, tenantId);
        ApiResponse<List<PolicyClauseResponse>> response = policyApi.getPolicyClauses(policyId, tenantId);
        if (!response.isSuccess()) {
            log.error("查询保单条款快照失败, policyId={}, error={}", policyId, response.getMessage());
            throw new BusinessException(ClaimErrorCode.POLICY_DETAIL_QUERY_FAILED,
                    "查询保单条款快照失败: " + response.getMessage());
        }
        List<PolicyClauseResponse> clauses = response.getData();
        if (clauses == null) {
            return List.of();
        }
        return clauses.stream()
                .map(this::toClauseRef)
                .toList();
    }

    @Override
    public List<BeneficiaryInfo> fetchBeneficiaries(String policyId, String tenantId) {
        log.info("查询保单受益人主数据, policyId={}, tenantId={}", policyId, tenantId);
        ApiResponse<List<PolicyBeneficiaryResponse>> response = policyApi.getBeneficiaries(policyId, tenantId);
        if (!response.isSuccess()) {
            log.error("查询保单受益人失败, policyId={}, error={}", policyId, response.getMessage());
            throw new BusinessException(ClaimErrorCode.POLICY_DETAIL_QUERY_FAILED,
                    "查询保单受益人失败: " + response.getMessage());
        }
        List<PolicyBeneficiaryResponse> beneficiaries = response.getData();
        if (beneficiaries == null) {
            return List.of();
        }
        return beneficiaries.stream()
                .map(this::toBeneficiaryInfo)
                .toList();
    }

    /**
     * 下游契约 → 领域条款引用摘要
     */
    private ClauseRef toClauseRef(PolicyClauseResponse clause) {
        return new ClauseRef(clause.getClauseId(), clause.getClauseCode(), clause.getClauseName(),
                clause.getMainClause());
    }

    /**
     * 下游契约 → 领域受益人摘要
     */
    private BeneficiaryInfo toBeneficiaryInfo(PolicyBeneficiaryResponse beneficiary) {
        return new BeneficiaryInfo(beneficiary.getBeneficiaryId(), beneficiary.getBeneficiaryName(),
                beneficiary.getBeneficiaryType(), beneficiary.getOrderNo(), beneficiary.getShareRatio());
    }
}
