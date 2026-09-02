package com.titanium.claim.infrastructure.repository.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.titanium.claim.infrastructure.entity.ClaimQuickPayRuleDO;

/**
 * 快赔规则 Spring Data 仓储（基础设施层，框架代理）
 * <p>
 * 逻辑删除约定：查询一律过滤 {@code isDeleted=0}；{@code findByTenantIdAndClaimType}
 * 不过滤（供 upsert 复用已删除行，规避唯一键冲突）。
 * </p>
 */
public interface JpaClaimQuickPayRuleRepository extends JpaRepository<ClaimQuickPayRuleDO, String> {

    Optional<ClaimQuickPayRuleDO> findByRuleIdAndTenantIdAndIsDeleted(String ruleId, String tenantId,
                                                                      Integer isDeleted);

    List<ClaimQuickPayRuleDO> findByTenantIdAndIsDeletedOrderByCreateTimeDesc(String tenantId, Integer isDeleted);

    Optional<ClaimQuickPayRuleDO> findByTenantIdAndClaimTypeAndIsDeleted(
            String tenantId, String claimType, Integer isDeleted);

    Optional<ClaimQuickPayRuleDO> findByTenantIdAndClaimType(String tenantId, String claimType);
}
