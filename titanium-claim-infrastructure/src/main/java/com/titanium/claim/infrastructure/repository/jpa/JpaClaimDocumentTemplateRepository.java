package com.titanium.claim.infrastructure.repository.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.titanium.claim.infrastructure.entity.ClaimDocumentTemplateDO;

/**
 * 单证模板 Spring Data 仓储（基础设施层，框架代理）
 * <p>
 * 逻辑删除约定：查询一律过滤 {@code isDeleted=0}；{@code findByTenantIdAndInsuranceLineAndClaimType}
 * 不过滤（供 upsert 复用已删除行，规避唯一键冲突）。
 * </p>
 */
public interface JpaClaimDocumentTemplateRepository extends JpaRepository<ClaimDocumentTemplateDO, String> {

    Optional<ClaimDocumentTemplateDO> findByTemplateIdAndTenantIdAndIsDeleted(String templateId, String tenantId,
                                                                              Integer isDeleted);

    List<ClaimDocumentTemplateDO> findByTenantIdAndIsDeletedOrderByCreateTimeDesc(String tenantId,
                                                                                  Integer isDeleted);

    Optional<ClaimDocumentTemplateDO> findByTenantIdAndInsuranceLineAndClaimTypeAndIsDeleted(
            String tenantId, String insuranceLine, String claimType, Integer isDeleted);

    Optional<ClaimDocumentTemplateDO> findByTenantIdAndInsuranceLineAndClaimType(
            String tenantId, String insuranceLine, String claimType);
}
