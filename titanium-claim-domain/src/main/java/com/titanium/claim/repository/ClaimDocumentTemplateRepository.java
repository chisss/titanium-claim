package com.titanium.claim.repository;

import java.util.List;
import java.util.Optional;

import com.titanium.claim.aggregate.ClaimDocumentTemplate;

/**
 * 单证模板聚合仓储接口（driven port，状态存储）
 * <p>
 * 物理实现位于 infrastructure 层（JPA 持久化 {@code t_claim_document_template}）。
 * 租户覆盖查询模式：先按租户业务键查，未命中回退平台默认（'platform'）。
 * </p>
 */
public interface ClaimDocumentTemplateRepository {

    /** 持久化（新增/全量更新，按 templateId upsert） */
    void save(ClaimDocumentTemplate template);

    /** 按租户 + 模板ID 查询 */
    Optional<ClaimDocumentTemplate> findById(String tenantId, String templateId);

    /** 查询租户下全部模板 */
    List<ClaimDocumentTemplate> findByTenant(String tenantId);

    /** 按业务键（险种线 × 理赔类型）查询租户模板 */
    Optional<ClaimDocumentTemplate> findByBusinessKey(String tenantId, String insuranceLine, String claimType);

    /** 按业务键查询平台默认模板（tenant_id='platform'） */
    Optional<ClaimDocumentTemplate> findPlatformDefault(String insuranceLine, String claimType);

    /** 逻辑删除 */
    void delete(String tenantId, String templateId);
}
