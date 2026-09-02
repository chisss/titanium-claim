package com.titanium.claim.application.command.config;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.claim.aggregate.ClaimDocumentTemplate;
import com.titanium.claim.application.model.config.ClaimDocumentTemplateConfigRequest;
import com.titanium.claim.common.context.TenantContext;
import com.titanium.claim.common.exception.BusinessException;
import com.titanium.claim.repository.ClaimDocumentTemplateRepository;
import com.titanium.common.util.SnowflakeIdGenerator;
import com.titanium.metadata.errorcode.ClaimErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 单证模板配置写服务（application 层配置子域写入口，薄）
 * <p>
 * 单聚合配置 CRUD：新增（雪花 ID）→ 聚合工厂（校验内聚）；更新 → 聚合 {@code update}（全量覆盖）。
 * 配置子域为状态存储支撑数据（根规约 §4.1 纯 JPA CRUD），不经过 Axon 命令网关、不发事件。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentTemplateConfigService {

    private final ClaimDocumentTemplateRepository repository;
    private final TenantContext                   tenantContext;

    /**
     * 新增/更新单证模板（templateId 空=新增，非空=全量更新）
     *
     * @return 模板ID
     */
    @Transactional
    public String saveDocumentTemplate(ClaimDocumentTemplateConfigRequest request) {
        String tenantId = tenantContext.getCurrentTenantId();
        if (request.getTemplateId() == null || request.getTemplateId().isBlank()) {
            ClaimDocumentTemplate template = ClaimDocumentTemplate.create(SnowflakeIdGenerator.generate(),
                    tenantId, request.getInsuranceLine(), request.getClaimType(),
                    request.getRequiredDocuments(), request.getOptionalDocuments());
            repository.save(template);
            log.info("新增单证模板, tenantId={}, templateId={}", tenantId, template.getTemplateId());
            return template.getTemplateId();
        }
        ClaimDocumentTemplate existing = repository.findById(tenantId, request.getTemplateId())
                .orElseThrow(() -> new BusinessException(ClaimErrorCode.CLAIM_CONFIG_NOT_FOUND,
                        "单证模板不存在: " + request.getTemplateId()));
        ClaimDocumentTemplate updated = existing.update(request.getInsuranceLine(), request.getClaimType(),
                request.getRequiredDocuments(), request.getOptionalDocuments());
        repository.save(updated);
        return updated.getTemplateId();
    }

    /**
     * 逻辑删除单证模板
     */
    @Transactional
    public void deleteDocumentTemplate(String templateId) {
        repository.delete(tenantContext.getCurrentTenantId(), templateId);
    }
}
