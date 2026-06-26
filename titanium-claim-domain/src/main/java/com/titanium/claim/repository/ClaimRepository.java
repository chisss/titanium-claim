package com.titanium.claim.repository;

import com.titanium.claim.aggregate.Claim;
import com.titanium.claim.enums.ClaimStatus;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.CustomerId;
import com.titanium.claim.valueobject.PolicyId;

import java.util.List;
import java.util.Optional;

/**
 * 理赔仓储接口（领域层抽象，实现在基础设施层）
 */
public interface ClaimRepository {

    /**
     * 根据理赔ID查找理赔记录
     * @param claimId 理赔ID
     * @return 理赔记录
     */
    Optional<Claim> findById(ClaimId claimId);

    /**
     * 根据客户ID查找所有理赔记录
     * @param customerId 客户ID
     * @return 理赔记录列表
     */
    List<Claim> findByCustomerId(CustomerId customerId);

    /**
     * 根据保单ID查找所有理赔记录
     * @param policyId 保单ID
     * @return 理赔记录列表
     */
    List<Claim> findByPolicyId(PolicyId policyId);

    /**
     * 根据状态查找理赔记录
     * @param status 理赔状态
     * @return 理赔记录列表
     */
    List<Claim> findByStatus(ClaimStatus status);

    /**
     * 根据理赔编号查找理赔记录
     * @param claimNumber 理赔编号
     * @return 理赔记录
     */
    Optional<Claim> findByClaimNumber(String claimNumber);

    /**
     * 查询全部理赔记录（读侧列表查询用）
     * @return 理赔记录列表
     */
    List<Claim> findAll();

    /**
     * 保存理赔记录
     * @param claim 理赔记录
     * @return 保存后的理赔记录
     */
    Claim save(Claim claim);

    /**
     * 删除理赔记录
     * @param claimId 理赔ID
     */
    void deleteById(ClaimId claimId);

    /**
     * 统计客户的理赔数量
     * @param customerId 客户ID
     * @return 理赔数量
     */
    long countByCustomerId(CustomerId customerId);

    /**
     * 统计保单的理赔数量
     * @param policyId 保单ID
     * @return 理赔数量
     */
    long countByPolicyId(PolicyId policyId);

    /**
     * 统计指定状态的理赔数量
     * @param status 理赔状态
     * @return 理赔数量
     */
    long countByStatus(ClaimStatus status);
}