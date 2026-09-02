package com.titanium.claim.infrastructure.repository.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.titanium.claim.infrastructure.entity.ClaimPayoutRuleDO;

/**
 * 赔付规则 Spring Data 仓储（基础设施层，框架代理）
 * <p>
 * 逻辑删除约定：查询一律过滤 {@code isDeleted=0}；{@code findByTenantIdAndInsuranceLineAndClaimType}
 * 不过滤（供 upsert 复用已删除行，规避唯一键冲突）。
 * </p>
 */
public interface JpaClaimPayoutRuleRepository extends JpaRepository<ClaimPayoutRuleDO, String> {

    Optional<ClaimPayoutRuleDO> findByRuleIdAndTenantIdAndIsDeleted(String ruleId, String tenantId,
                                                                    Integer isDeleted);

    List<ClaimPayoutRuleDO> findByTenantIdAndIsDeletedOrderByCreateTimeDesc(String tenantId, Integer isDeleted);

    Optional<ClaimPayoutRuleDO> findByTenantIdAndInsuranceLineAndClaimTypeAndIsDeleted(
            String tenantId, String insuranceLine, String claimType, Integer isDeleted);

    Optional<ClaimPayoutRuleDO> findByTenantIdAndInsuranceLineAndClaimType(
            String tenantId, String insuranceLine, String claimType);
}
