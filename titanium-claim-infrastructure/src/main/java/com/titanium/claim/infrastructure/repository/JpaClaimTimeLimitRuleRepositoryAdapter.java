package com.titanium.claim.infrastructure.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.claim.aggregate.ClaimTimeLimitRule;
import com.titanium.claim.common.constant.ClaimConstants;
import com.titanium.claim.infrastructure.entity.ClaimTimeLimitRuleDO;
import com.titanium.claim.infrastructure.mapper.ClaimConfigPersistenceMapper;
import com.titanium.claim.infrastructure.repository.jpa.JpaClaimTimeLimitRuleRepository;
import com.titanium.claim.repository.ClaimTimeLimitRuleRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 时限规则仓储实现（domain 仓储接口的 JPA Adapter）
 * <p>
 * 状态存储聚合：聚合 → DO 经 MapStruct 转换；DO → 聚合经 {@code ClaimTimeLimitRule.create}
 * 工厂重建。upsert 按业务键复用行（含逻辑删除行复活），规避唯一约束冲突；审计字段由仓储显式维护。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JpaClaimTimeLimitRuleRepositoryAdapter implements ClaimTimeLimitRuleRepository {

    private final JpaClaimTimeLimitRuleRepository jpaRepository;
    private final ClaimConfigPersistenceMapper   mapper;

    @Override
    @Transactional
    public void save(ClaimTimeLimitRule rule) {
        Optional<ClaimTimeLimitRuleDO> existing = jpaRepository.findByTenantIdAndInsuranceLineAndClaimStage(
                rule.getTenantId(), rule.getInsuranceLine(), rule.getClaimStage());
        ClaimTimeLimitRuleDO fresh = mapper.toDO(rule);
        if (existing.isPresent()) {
            ClaimTimeLimitRuleDO old = existing.get();
            fresh.setRuleId(old.getRuleId());
            fresh.setId(old.getId());
            fresh.setCreateTime(old.getCreateTime());
            fresh.setCreatedBy(old.getCreatedBy());
            fresh.setIsDeleted(0);
        } else {
            fresh.setId(rule.getRuleId());
            fresh.setCreateTime(LocalDateTime.now());
            fresh.setCreatedBy(ClaimConstants.SYSTEM_OPERATOR);
            fresh.setIsDeleted(0);
        }
        fresh.setUpdateTime(LocalDateTime.now());
        fresh.setUpdatedBy(ClaimConstants.SYSTEM_OPERATOR);
        jpaRepository.save(fresh);
    }

    @Override
    public Optional<ClaimTimeLimitRule> findById(String tenantId, String ruleId) {
        return jpaRepository.findByRuleIdAndTenantIdAndIsDeleted(ruleId, tenantId, 0)
                .map(this::toDomain);
    }

    @Override
    public List<ClaimTimeLimitRule> findByTenant(String tenantId) {
        return jpaRepository.findByTenantIdAndIsDeletedOrderByCreateTimeDesc(tenantId, 0).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<ClaimTimeLimitRule> findByBusinessKey(String tenantId, String insuranceLine,
                                                          String claimStage) {
        return jpaRepository.findByTenantIdAndInsuranceLineAndClaimStageAndIsDeleted(
                tenantId, insuranceLine, claimStage, 0).map(this::toDomain);
    }

    @Override
    public Optional<ClaimTimeLimitRule> findPlatformDefault(String insuranceLine, String claimStage) {
        return findByBusinessKey("platform", insuranceLine, claimStage);
    }

    @Override
    @Transactional
    public void delete(String tenantId, String ruleId) {
        jpaRepository.findByRuleIdAndTenantIdAndIsDeleted(ruleId, tenantId, 0).ifPresent(dataObject -> {
            dataObject.setIsDeleted(1);
            dataObject.setUpdateTime(LocalDateTime.now());
            dataObject.setUpdatedBy(ClaimConstants.SYSTEM_OPERATOR);
            jpaRepository.save(dataObject);
            log.info("逻辑删除时限规则, tenantId={}, ruleId={}", tenantId, ruleId);
        });
    }

    private ClaimTimeLimitRule toDomain(ClaimTimeLimitRuleDO dataObject) {
        return ClaimTimeLimitRule.create(dataObject.getRuleId(), dataObject.getTenantId(), dataObject.getInsuranceLine(),
                dataObject.getClaimStage(), dataObject.getLimitHours(), dataObject.getAlertHours());
    }
}
