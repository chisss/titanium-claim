package com.titanium.claim.repository;

import java.util.List;
import java.util.Optional;

import com.titanium.claim.aggregate.ClaimTimeLimitRule;

/**
 * 时限规则聚合仓储接口（driven port，状态存储）
 * <p>
 * 物理实现位于 infrastructure 层（JPA 持久化 {@code t_claim_time_limit_rule}）。
 * 租户覆盖查询模式：先按租户业务键查，未命中回退平台默认（'platform'）。
 * </p>
 */
public interface ClaimTimeLimitRuleRepository {

    /** 持久化（新增/全量更新，按 ruleId upsert） */
    void save(ClaimTimeLimitRule rule);

    /** 按租户 + 规则ID 查询 */
    Optional<ClaimTimeLimitRule> findById(String tenantId, String ruleId);

    /** 查询租户下全部规则 */
    List<ClaimTimeLimitRule> findByTenant(String tenantId);

    /** 按业务键（险种线 × 案件环节）查询租户规则 */
    Optional<ClaimTimeLimitRule> findByBusinessKey(String tenantId, String insuranceLine, String claimStage);

    /** 按业务键查询平台默认规则（tenant_id='platform'） */
    Optional<ClaimTimeLimitRule> findPlatformDefault(String insuranceLine, String claimStage);

    /** 逻辑删除 */
    void delete(String tenantId, String ruleId);
}
