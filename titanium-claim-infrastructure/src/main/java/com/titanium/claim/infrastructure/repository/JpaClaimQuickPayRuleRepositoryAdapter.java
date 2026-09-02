package com.titanium.claim.infrastructure.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.claim.aggregate.ClaimQuickPayRule;
import com.titanium.claim.infrastructure.entity.ClaimQuickPayRuleDO;
import com.titanium.claim.infrastructure.mapper.ClaimConfigPersistenceMapper;
import com.titanium.claim.infrastructure.repository.jpa.JpaClaimQuickPayRuleRepository;
import com.titanium.claim.repository.ClaimQuickPayRuleRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 快赔规则仓储实现（domain 仓储接口的 JPA Adapter）
 * <p>
 * 状态存储聚合：聚合 → DO 经 MapStruct 转换；DO → 聚合经 {@code ClaimQuickPayRule.create}
 * 工厂重建。upsert 按业务键复用行（含逻辑删除行复活），规避唯一约束冲突；审计字段由仓储显式维护。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JpaClaimQuickPayRuleRepositoryAdapter implements ClaimQuickPayRuleRepository {

    private final JpaClaimQuickPayRuleRepository jpaRepository;
    private final ClaimConfigPersistenceMapper  mapper;

    @Override
    @Transactional
    public void save(ClaimQuickPayRule rule) {
        Optional<ClaimQuickPayRuleDO> existing = jpaRepository.findByTenantIdAndClaimType(
                rule.getTenantId(), rule.getClaimType());
        ClaimQuickPayRuleDO fresh = mapper.toDO(rule);
        if (existing.isPresent()) {
            ClaimQuickPayRuleDO old = existing.get();
            fresh.setRuleId(old.getRuleId());
            fresh.setCreateTime(old.getCreateTime());
            fresh.setIsDeleted(0);
        } else {
            fresh.setCreateTime(LocalDateTime.now());
            fresh.setIsDeleted(0);
        }
        fresh.setUpdateTime(LocalDateTime.now());
        jpaRepository.save(fresh);
    }

    @Override
    public Optional<ClaimQuickPayRule> findById(String tenantId, String ruleId) {
        return jpaRepository.findByRuleIdAndTenantIdAndIsDeleted(ruleId, tenantId, 0)
                .map(this::toDomain);
    }

    @Override
    public List<ClaimQuickPayRule> findByTenant(String tenantId) {
        return jpaRepository.findByTenantIdAndIsDeletedOrderByCreateTimeDesc(tenantId, 0).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<ClaimQuickPayRule> findByBusinessKey(String tenantId, String claimType) {
        return jpaRepository.findByTenantIdAndClaimTypeAndIsDeleted(tenantId, claimType, 0)
                .map(this::toDomain);
    }

    @Override
    public Optional<ClaimQuickPayRule> findPlatformDefault(String claimType) {
        return findByBusinessKey("platform", claimType);
    }

    @Override
    @Transactional
    public void delete(String tenantId, String ruleId) {
        jpaRepository.findByRuleIdAndTenantIdAndIsDeleted(ruleId, tenantId, 0).ifPresent(dataObject -> {
            dataObject.setIsDeleted(1);
            dataObject.setUpdateTime(LocalDateTime.now());
            jpaRepository.save(dataObject);
            log.info("逻辑删除快赔规则, tenantId={}, ruleId={}", tenantId, ruleId);
        });
    }

    private ClaimQuickPayRule toDomain(ClaimQuickPayRuleDO dataObject) {
        return ClaimQuickPayRule.create(dataObject.getRuleId(), dataObject.getTenantId(),
                dataObject.getClaimType(), Boolean.TRUE.equals(dataObject.getEnabled()),
                dataObject.getAmountThreshold());
    }
}
