package com.titanium.claim.infrastructure.repository.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.titanium.claim.infrastructure.entity.ClaimBlacklistDO;

/**
 * 黑名单 Spring Data 仓储（基础设施层，框架代理）
 * <p>
 * 逻辑删除约定：查询一律过滤 {@code isDeleted=0}。
 * </p>
 */
public interface JpaClaimBlacklistRepository extends JpaRepository<ClaimBlacklistDO, String> {

    Optional<ClaimBlacklistDO> findByBlacklistIdAndTenantIdAndIsDeleted(String blacklistId, String tenantId,
                                                                        Integer isDeleted);

    List<ClaimBlacklistDO> findByTenantIdAndIsDeletedOrderByCreateTimeDesc(String tenantId, Integer isDeleted);

    Optional<ClaimBlacklistDO> findByTenantIdAndSubjectTypeAndSubjectIdAndStatusAndIsDeleted(
            String tenantId, String subjectType, String subjectId, String status, Integer isDeleted);
}
