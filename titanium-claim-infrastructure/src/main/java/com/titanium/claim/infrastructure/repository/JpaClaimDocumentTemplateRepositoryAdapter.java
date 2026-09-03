package com.titanium.claim.infrastructure.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.claim.aggregate.ClaimDocumentTemplate;
import com.titanium.claim.common.constant.ClaimConstants;
import com.titanium.claim.infrastructure.entity.ClaimDocumentTemplateDO;
import com.titanium.claim.infrastructure.mapper.ClaimConfigPersistenceMapper;
import com.titanium.claim.infrastructure.repository.jpa.JpaClaimDocumentTemplateRepository;
import com.titanium.claim.repository.ClaimDocumentTemplateRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 单证模板仓储实现（domain 仓储接口的 JPA Adapter）
 * <p>
 * 状态存储聚合：聚合 → DO 经 MapStruct 转换；DO → 聚合经 {@code ClaimDocumentTemplate.create}
 * 工厂重建。upsert 按业务键复用行（含逻辑删除行复活），规避唯一约束冲突；审计字段由仓储显式维护。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JpaClaimDocumentTemplateRepositoryAdapter implements ClaimDocumentTemplateRepository {

    private final JpaClaimDocumentTemplateRepository jpaRepository;
    private final ClaimConfigPersistenceMapper      mapper;

    @Override
    @Transactional
    public void save(ClaimDocumentTemplate template) {
        Optional<ClaimDocumentTemplateDO> existing = jpaRepository.findByTenantIdAndInsuranceLineAndClaimType(
                template.getTenantId(), template.getInsuranceLine(), template.getClaimType());
        ClaimDocumentTemplateDO fresh = mapper.toDO(template);
        if (existing.isPresent()) {
            ClaimDocumentTemplateDO old = existing.get();
            fresh.setTemplateId(old.getTemplateId());
            fresh.setId(old.getId());
            fresh.setCreateTime(old.getCreateTime());
            fresh.setCreatedBy(old.getCreatedBy());
            fresh.setIsDeleted(0);
        } else {
            fresh.setId(template.getTemplateId());
            fresh.setCreateTime(LocalDateTime.now());
            fresh.setCreatedBy(ClaimConstants.SYSTEM_OPERATOR);
            fresh.setIsDeleted(0);
        }
        fresh.setUpdateTime(LocalDateTime.now());
        fresh.setUpdatedBy(ClaimConstants.SYSTEM_OPERATOR);
        jpaRepository.save(fresh);
    }

    @Override
    public Optional<ClaimDocumentTemplate> findById(String tenantId, String templateId) {
        return jpaRepository.findByTemplateIdAndTenantIdAndIsDeleted(templateId, tenantId, 0)
                .map(this::toDomain);
    }

    @Override
    public List<ClaimDocumentTemplate> findByTenant(String tenantId) {
        return jpaRepository.findByTenantIdAndIsDeletedOrderByCreateTimeDesc(tenantId, 0).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<ClaimDocumentTemplate> findByBusinessKey(String tenantId, String insuranceLine,
                                                             String claimType) {
        return jpaRepository.findByTenantIdAndInsuranceLineAndClaimTypeAndIsDeleted(
                tenantId, insuranceLine, claimType, 0).map(this::toDomain);
    }

    @Override
    public Optional<ClaimDocumentTemplate> findPlatformDefault(String insuranceLine, String claimType) {
        return findByBusinessKey("platform", insuranceLine, claimType);
    }

    @Override
    @Transactional
    public void delete(String tenantId, String templateId) {
        jpaRepository.findByTemplateIdAndTenantIdAndIsDeleted(templateId, tenantId, 0).ifPresent(dataObject -> {
            dataObject.setIsDeleted(1);
            dataObject.setUpdateTime(LocalDateTime.now());
            dataObject.setUpdatedBy(ClaimConstants.SYSTEM_OPERATOR);
            jpaRepository.save(dataObject);
            log.info("逻辑删除单证模板, tenantId={}, templateId={}", tenantId, templateId);
        });
    }

    private ClaimDocumentTemplate toDomain(ClaimDocumentTemplateDO dataObject) {
        return ClaimDocumentTemplate.create(dataObject.getTemplateId(), dataObject.getTenantId(), dataObject.getInsuranceLine(),
                dataObject.getClaimType(), JsonSupport.toStringList(dataObject.getRequiredDocuments()),
                JsonSupport.toStringList(dataObject.getOptionalDocuments()));
    }
}
