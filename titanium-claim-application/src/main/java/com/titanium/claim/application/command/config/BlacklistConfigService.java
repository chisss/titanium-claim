package com.titanium.claim.application.command.config;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.claim.aggregate.ClaimBlacklist;
import com.titanium.claim.application.model.config.ClaimBlacklistConfigRequest;
import com.titanium.claim.common.context.TenantContext;
import com.titanium.claim.common.enums.config.BlacklistSubjectType;
import com.titanium.claim.common.exception.BusinessException;
import com.titanium.claim.repository.ClaimBlacklistRepository;
import com.titanium.common.util.SnowflakeIdGenerator;
import com.titanium.metadata.errorcode.ClaimErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 黑名单配置写服务（application 层配置子域写入口，薄）
 * <p>
 * 黑名单台账 CRUD + 撤销（聚合行为内聚）。配置子域为状态存储支撑数据（根规约 §4.1 纯 JPA CRUD），
 * 不经过 Axon 命令网关、不发事件；命中提示由 dev-012 反欺诈警示消费 {@code findActiveBySubject}。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlacklistConfigService {

    private final ClaimBlacklistRepository repository;
    private final TenantContext            tenantContext;

    /**
     * 新增/更新黑名单（blacklistId 空=新增，非空=全量更新）
     *
     * @return 黑名单ID
     */
    @Transactional
    public String saveBlacklist(ClaimBlacklistConfigRequest request) {
        String tenantId = tenantContext.getCurrentTenantId();
        if (request.getBlacklistId() == null || request.getBlacklistId().isBlank()) {
            ClaimBlacklist blacklist = ClaimBlacklist.create(SnowflakeIdGenerator.generate(), tenantId,
                    BlacklistSubjectType.fromCode(request.getSubjectType()), request.getSubjectId(),
                    request.getSubjectName(), request.getReasonCode(), request.getEffectiveTime());
            repository.save(blacklist);
            log.info("新增黑名单, tenantId={}, blacklistId={}", tenantId, blacklist.getBlacklistId());
            return blacklist.getBlacklistId();
        }
        ClaimBlacklist existing = repository.findById(tenantId, request.getBlacklistId())
                .orElseThrow(() -> new BusinessException(ClaimErrorCode.CLAIM_CONFIG_NOT_FOUND,
                        "黑名单不存在: " + request.getBlacklistId()));
        ClaimBlacklist updated = existing.update(request.getSubjectType(), request.getSubjectId(),
                request.getSubjectName(), request.getReasonCode(), request.getEffectiveTime());
        repository.save(updated);
        return updated.getBlacklistId();
    }

    /**
     * 撤销黑名单：留存记录，不再参与命中提示
     */
    @Transactional
    public void revokeBlacklist(String blacklistId) {
        repository.findById(tenantContext.getCurrentTenantId(), blacklistId)
                .ifPresentOrElse(blacklist -> repository.save(blacklist.revoke()),
                        () -> { throw new BusinessException(ClaimErrorCode.CLAIM_CONFIG_NOT_FOUND,
                                "黑名单不存在: " + blacklistId); });
    }

    /**
     * 逻辑删除黑名单
     */
    @Transactional
    public void deleteBlacklist(String blacklistId) {
        repository.delete(tenantContext.getCurrentTenantId(), blacklistId);
    }
}
