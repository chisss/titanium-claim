package com.titanium.claim.repository;

import java.util.List;
import java.util.Optional;

import com.titanium.claim.aggregate.ClaimPayoutRule;

/**
 * 赔付规则聚合仓储接口（driven port，状态存储）
 * <p>
 * 物理实现位于 infrastructure 层（JPA 持久化 {@code t_claim_payout_rule}）。
 * 租户覆盖查询模式：先按租户业务键查，未命中回退平台默认（'platform'）。
 * </p>
 */
public interface ClaimPayoutRuleRepository {

    /** 持久化（新增/全量更新，按 ruleId upsert） */
    void save(ClaimPayoutRule rule);

    /** 按租户 + 规则ID 查询 */
    Optional<ClaimPayoutRule> findById(String tenantId, String ruleId);

    /** 查询租户下全部规则 */
    List<ClaimPayoutRule> findByTenant(String tenantId);

    /** 按业务键（险种线 × 理赔类型）查询租户规则 */
    Optional<ClaimPayoutRule> findByBusinessKey(String tenantId, String insuranceLine, String claimType);

    /** 按业务键查询平台默认规则（tenant_id='platform'） */
    Optional<ClaimPayoutRule> findPlatformDefault(String insuranceLine, String claimType);

    /** 逻辑删除 */
    void delete(String tenantId, String ruleId);
}
