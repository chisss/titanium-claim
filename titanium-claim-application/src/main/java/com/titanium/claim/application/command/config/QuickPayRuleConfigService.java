package com.titanium.claim.application.command.config;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.claim.aggregate.ClaimQuickPayRule;
import com.titanium.claim.application.model.config.ClaimQuickPayRuleConfigRequest;
import com.titanium.claim.common.context.TenantContext;
import com.titanium.claim.common.exception.BusinessException;
import com.titanium.claim.repository.ClaimQuickPayRuleRepository;
import com.titanium.common.util.SnowflakeIdGenerator;
import com.titanium.metadata.errorcode.ClaimErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 快赔规则配置写服务（application 层配置子域写入口，薄）
 * <p>
 * 单聚合配置 CRUD：新增（雪花 ID）→ 聚合工厂（校验内聚：阈值 > 0、理赔类型非空）；
 * 更新 → 聚合 {@code update}（全量覆盖）。配置子域为状态存储支撑数据（根规约 §4.1 纯 JPA CRUD），
 * 不经过 Axon 命令网关、不发事件。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuickPayRuleConfigService {

    private final ClaimQuickPayRuleRepository repository;
    private final TenantContext               tenantContext;

    /**
     * 新增/更新快赔规则（ruleId 空=新增，非空=全量更新）
     *
     * @return 规则ID
     */
    @Transactional
    public String saveQuickPayRule(ClaimQuickPayRuleConfigRequest request) {
        String tenantId = tenantContext.getCurrentTenantId();
        if (request.getRuleId() == null || request.getRuleId().isBlank()) {
            ClaimQuickPayRule rule = ClaimQuickPayRule.create(SnowflakeIdGenerator.generate(), tenantId,
                    request.getClaimType(), Boolean.TRUE.equals(request.getEnabled()),
                    request.getAmountThreshold());
            repository.save(rule);
            log.info("新增快赔规则, tenantId={}, ruleId={}", tenantId, rule.getRuleId());
            return rule.getRuleId();
        }
        ClaimQuickPayRule existing = repository.findById(tenantId, request.getRuleId())
                .orElseThrow(() -> new BusinessException(ClaimErrorCode.CLAIM_CONFIG_NOT_FOUND,
                        "快赔规则不存在: " + request.getRuleId()));
        ClaimQuickPayRule updated = existing.update(request.getClaimType(),
                Boolean.TRUE.equals(request.getEnabled()), request.getAmountThreshold());
        repository.save(updated);
        return updated.getRuleId();
    }

    /**
     * 逻辑删除快赔规则
     */
    @Transactional
    public void deleteQuickPayRule(String ruleId) {
        repository.delete(tenantContext.getCurrentTenantId(), ruleId);
    }
}
