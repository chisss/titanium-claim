package com.titanium.claim.repository;

import java.util.List;
import java.util.Optional;

import com.titanium.claim.aggregate.ClaimBlacklist;
import com.titanium.claim.common.enums.config.BlacklistSubjectType;

/**
 * 黑名单聚合仓储接口（driven port，状态存储）
 * <p>
 * 物理实现位于 infrastructure 层（JPA 持久化 {@code t_claim_blacklist}）。
 * 反欺诈命中提示按「标的类型 × 标的ID」查生效条目。
 * </p>
 */
public interface ClaimBlacklistRepository {

    /** 持久化（新增/全量更新，按 blacklistId upsert） */
    void save(ClaimBlacklist blacklist);

    /** 按租户 + 黑名单ID 查询 */
    Optional<ClaimBlacklist> findById(String tenantId, String blacklistId);

    /** 查询租户下全部条目 */
    List<ClaimBlacklist> findByTenant(String tenantId);

    /** 按标的查租户生效条目（反欺诈命中入口，仅返回 ACTIVE 态） */
    Optional<ClaimBlacklist> findActiveBySubject(String tenantId, BlacklistSubjectType subjectType,
                                                 String subjectId);

    /** 逻辑删除 */
    void delete(String tenantId, String blacklistId);
}
