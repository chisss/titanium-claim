package com.titanium.claim.application.query;

import com.titanium.claim.aggregate.Claim;
import com.titanium.claim.query.ClaimQuery;
import com.titanium.claim.query.FindClaimsByCustomerIdQuery;
import com.titanium.claim.query.FindClaimsByPolicyIdQuery;
import com.titanium.claim.query.FindClaimsByStatusQuery;
import com.titanium.claim.repository.ClaimRepository;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.CustomerId;
import com.titanium.claim.valueobject.PolicyId;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 理赔案件查询应用服务
 * <p>
 * 处理理赔案件相关的查询，协调领域层和基础设施层
 * </p>
 */
@Service
@AllArgsConstructor
public class ClaimAppQueryService {
    private final ClaimRepository claimRepository;

    /**
     * 根据ID查询理赔案件
     *
     * @param query 查询条件
     * @return 理赔案件
     */
    public Optional<Claim> findClaimById(ClaimQuery query) {
        return claimRepository.findById(query.claimId());
    }

    /**
     * 根据保单ID查询理赔案件列表
     *
     * @param query 查询条件
     * @return 理赔案件列表
     */
    public List<Claim> findClaimsByPolicyId(FindClaimsByPolicyIdQuery query) {
        return claimRepository.findByPolicyId(query.policyId());
    }

    /**
     * 根据客户ID查询理赔案件列表
     *
     * @param query 查询条件
     * @return 理赔案件列表
     */
    public List<Claim> findClaimsByCustomerId(FindClaimsByCustomerIdQuery query) {
        return claimRepository.findByCustomerId(query.customerId());
    }

    /**
     * 根据状态查询理赔案件列表
     *
     * @param query 查询条件
     * @return 理赔案件列表
     */
    public List<Claim> findClaimsByStatus(FindClaimsByStatusQuery query) {
        return claimRepository.findByStatus(query.status());
    }

    /**
     * 根据理赔编号查询理赔案件
     *
     * @param claimNumber 理赔编号
     * @return 理赔案件
     */
    public Optional<Claim> findByClaimNumber(String claimNumber) {
        return claimRepository.findByClaimNumber(claimNumber);
    }

    /**
     * 统计客户的理赔案件数量
     *
     * @param customerId 客户ID
     * @return 理赔案件数量
     */
    public long countByCustomerId(CustomerId customerId) {
        return claimRepository.countByCustomerId(customerId);
    }

    /**
     * 统计保单的理赔案件数量
     *
     * @param policyId 保单ID
     * @return 理赔案件数量
     */
    public long countByPolicyId(PolicyId policyId) {
        return claimRepository.countByPolicyId(policyId);
    }
}