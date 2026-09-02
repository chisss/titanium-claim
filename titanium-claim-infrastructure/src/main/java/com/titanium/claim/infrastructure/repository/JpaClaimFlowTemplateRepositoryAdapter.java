package com.titanium.claim.infrastructure.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.claim.aggregate.ClaimFlowTemplate;
import com.titanium.claim.infrastructure.entity.ClaimFlowTemplateDO;
import com.titanium.claim.infrastructure.mapper.ClaimConfigPersistenceMapper;
import com.titanium.claim.infrastructure.repository.jpa.JpaClaimFlowTemplateRepository;
import com.titanium.claim.repository.ClaimFlowTemplateRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 流程模板仓储实现（domain 仓储接口的 JPA Adapter）
 * <p>
 * 状态存储聚合：聚合 → DO 经 MapStruct 转换；DO → 聚合经 {@code ClaimFlowTemplate.create}
 * 工厂重建（构造校验内聚在聚合内）。upsert 按业务键复用行（含逻辑删除行复活），规避
 * {@code (tenant_id, insurance_line, claim_type)} 唯一约束冲突。审计字段由仓储显式维护
 * （BasePersistable 不启用 JPA Auditing 自动填充）。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JpaClaimFlowTemplateRepositoryAdapter implements ClaimFlowTemplateRepository {

    private final JpaClaimFlowTemplateRepository jpaRepository;
    private final ClaimConfigPersistenceMapper  mapper;

    @Override
    @Transactional
    public void save(ClaimFlowTemplate template) {
        Optional<ClaimFlowTemplateDO> existing = jpaRepository.findByTenantIdAndInsuranceLineAndClaimType(
                template.getTenantId(), template.getInsuranceLine(), template.getClaimType());
        ClaimFlowTemplateDO fresh = mapper.toDO(template);
        if (existing.isPresent()) {
            // 业务键已存在（含逻辑删除行）：保留原主键与创建时间，覆盖内容并复活
            ClaimFlowTemplateDO old = existing.get();
            fresh.setTemplateId(old.getTemplateId());
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
    public Optional<ClaimFlowTemplate> findById(String tenantId, String templateId) {
        return jpaRepository.findByTemplateIdAndTenantIdAndIsDeleted(templateId, tenantId, 0)
                .map(this::toDomain);
    }

    @Override
    public List<ClaimFlowTemplate> findByTenant(String tenantId) {
        return jpaRepository.findByTenantIdAndIsDeletedOrderByCreateTimeDesc(tenantId, 0).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<ClaimFlowTemplate> findByBusinessKey(String tenantId, String insuranceLine, String claimType) {
        return jpaRepository.findByTenantIdAndInsuranceLineAndClaimTypeAndIsDeleted(
                tenantId, insuranceLine, claimType, 0).map(this::toDomain);
    }

    @Override
    public Optional<ClaimFlowTemplate> findPlatformDefault(String insuranceLine, String claimType) {
        return findByBusinessKey("platform", insuranceLine, claimType);
    }

    @Override
    @Transactional
    public void delete(String tenantId, String templateId) {
        jpaRepository.findByTemplateIdAndTenantIdAndIsDeleted(templateId, tenantId, 0).ifPresent(dataObject -> {
            dataObject.setIsDeleted(1);
            dataObject.setUpdateTime(LocalDateTime.now());
            jpaRepository.save(dataObject);
            log.info("逻辑删除流程模板, tenantId={}, templateId={}", tenantId, templateId);
        });
    }

    private ClaimFlowTemplate toDomain(ClaimFlowTemplateDO dataObject) {
        return ClaimFlowTemplate.create(dataObject.getTemplateId(), dataObject.getTenantId(), dataObject.getInsuranceLine(),
                dataObject.getClaimType(), JsonSupport.toStringList(dataObject.getStageSequence()),
                JsonSupport.toStringIntegerMap(dataObject.getStageTimeLimits()), dataObject.getResponsibleRole(),
                JsonSupport.toStringList(dataObject.getMandatoryCheckpoints()));
    }
}
