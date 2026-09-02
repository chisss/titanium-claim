package com.titanium.claim.infrastructure.repository.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.titanium.claim.infrastructure.entity.ClaimTimeLimitRuleDO;

/**
 * 时限规则 Spring Data 仓储（基础设施层，框架代理）
 * <p>
 * 逻辑删除约定：查询一律过滤 {@code isDeleted=0}；{@code findByTenantIdAndInsuranceLineAndClaimStage}
 * 不过滤（供 upsert 复用已删除行，规避唯一键冲突）。
 * </p>
 */
public interface JpaClaimTimeLimitRuleRepository extends JpaRepository<ClaimTimeLimitRuleDO, String> {

    Optional<ClaimTimeLimitRuleDO> findByRuleIdAndTenantIdAndIsDeleted(String ruleId, String tenantId,
                                                                       Integer isDeleted);

    List<ClaimTimeLimitRuleDO> findByTenantIdAndIsDeletedOrderByCreateTimeDesc(String tenantId, Integer isDeleted);

    Optional<ClaimTimeLimitRuleDO> findByTenantIdAndInsuranceLineAndClaimStageAndIsDeleted(
            String tenantId, String insuranceLine, String claimStage, Integer isDeleted);

    Optional<ClaimTimeLimitRuleDO> findByTenantIdAndInsuranceLineAndClaimStage(
            String tenantId, String insuranceLine, String claimStage);
}
