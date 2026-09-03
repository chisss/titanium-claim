package com.titanium.claim.infrastructure.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.claim.aggregate.ClaimBlacklist;
import com.titanium.claim.common.constant.ClaimConstants;
import com.titanium.claim.common.enums.config.BlacklistStatus;
import com.titanium.claim.common.enums.config.BlacklistSubjectType;
import com.titanium.claim.infrastructure.entity.ClaimBlacklistDO;
import com.titanium.claim.infrastructure.mapper.ClaimConfigPersistenceMapper;
import com.titanium.claim.infrastructure.repository.jpa.JpaClaimBlacklistRepository;
import com.titanium.claim.repository.ClaimBlacklistRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 黑名单仓储实现（domain 仓储接口的 JPA Adapter）
 * <p>
 * 状态存储聚合：聚合 → DO 经 MapStruct 转换；DO → 聚合经 {@code ClaimBlacklist.create}/
 * {@code ClaimBlacklist.revoke} 工厂重建。按 blacklistId 主键 upsert；审计字段由仓储显式维护。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JpaClaimBlacklistRepositoryAdapter implements ClaimBlacklistRepository {

    private final JpaClaimBlacklistRepository  jpaRepository;
    private final ClaimConfigPersistenceMapper mapper;

    @Override
    @Transactional
    public void save(ClaimBlacklist blacklist) {
        Optional<ClaimBlacklistDO> existing = jpaRepository.findById(blacklist.getBlacklistId());
        ClaimBlacklistDO fresh = mapper.toDO(blacklist);
        if (existing.isPresent()) {
            fresh.setId(existing.get().getId());
            fresh.setCreateTime(existing.get().getCreateTime());
            fresh.setCreatedBy(existing.get().getCreatedBy());
        } else {
            fresh.setId(blacklist.getBlacklistId());
            fresh.setCreateTime(LocalDateTime.now());
            fresh.setCreatedBy(ClaimConstants.SYSTEM_OPERATOR);
        }
        fresh.setIsDeleted(0);
        fresh.setUpdateTime(LocalDateTime.now());
        fresh.setUpdatedBy(ClaimConstants.SYSTEM_OPERATOR);
        jpaRepository.save(fresh);
    }

    @Override
    public Optional<ClaimBlacklist> findById(String tenantId, String blacklistId) {
        return jpaRepository.findByBlacklistIdAndTenantIdAndIsDeleted(blacklistId, tenantId, 0)
                .map(this::toDomain);
    }

    @Override
    public List<ClaimBlacklist> findByTenant(String tenantId) {
        return jpaRepository.findByTenantIdAndIsDeletedOrderByCreateTimeDesc(tenantId, 0).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<ClaimBlacklist> findActiveBySubject(String tenantId, BlacklistSubjectType subjectType,
                                                        String subjectId) {
        return jpaRepository.findByTenantIdAndSubjectTypeAndSubjectIdAndStatusAndIsDeleted(
                tenantId, subjectType.getCode(), subjectId, BlacklistStatus.ACTIVE.getCode(), 0)
                .map(this::toDomain);
    }

    @Override
    @Transactional
    public void delete(String tenantId, String blacklistId) {
        jpaRepository.findByBlacklistIdAndTenantIdAndIsDeleted(blacklistId, tenantId, 0).ifPresent(dataObject -> {
            dataObject.setIsDeleted(1);
            dataObject.setUpdateTime(LocalDateTime.now());
            dataObject.setUpdatedBy(ClaimConstants.SYSTEM_OPERATOR);
            jpaRepository.save(dataObject);
            log.info("逻辑删除黑名单, tenantId={}, blacklistId={}", tenantId, blacklistId);
        });
    }

    private ClaimBlacklist toDomain(ClaimBlacklistDO dataObject) {
        ClaimBlacklist blacklist = ClaimBlacklist.create(dataObject.getBlacklistId(), dataObject.getTenantId(),
                BlacklistSubjectType.fromCode(dataObject.getSubjectType()), dataObject.getSubjectId(), dataObject.getSubjectName(),
                dataObject.getReasonCode(), dataObject.getEffectiveTime());
        // 已撤销条目按 REVOKED 态还原（create 工厂默认 ACTIVE）
        return BlacklistStatus.fromCode(dataObject.getStatus()) == BlacklistStatus.REVOKED
                ? blacklist.revoke()
                : blacklist;
    }
}
