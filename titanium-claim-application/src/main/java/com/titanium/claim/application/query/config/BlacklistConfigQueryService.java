package com.titanium.claim.application.query.config;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.claim.aggregate.ClaimBlacklist;
import com.titanium.claim.common.context.TenantContext;
import com.titanium.claim.common.exception.BusinessException;
import com.titanium.claim.repository.ClaimBlacklistRepository;
import com.titanium.metadata.errorcode.ClaimErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 黑名单配置查询服务（application 层配置子域读入口）
 * <p>
 * 配置子域为状态存储（无读模型投影），读请求直接查写侧仓储（§3.4.9 ⑥ 领域内部查询走写模型聚合），
 * 返回领域聚合由 web 层 MapStruct 组装 VO。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class BlacklistConfigQueryService {

    private final ClaimBlacklistRepository repository;
    private final TenantContext            tenantContext;

    /**
     * 按黑名单ID查询
     */
    @Transactional(readOnly = true)
    public ClaimBlacklist getBlacklist(String blacklistId) {
        return repository.findById(tenantContext.getCurrentTenantId(), blacklistId)
                .orElseThrow(() -> new BusinessException(ClaimErrorCode.CLAIM_CONFIG_NOT_FOUND,
                        "黑名单不存在: " + blacklistId));
    }

    /**
     * 查询租户下全部条目
     */
    @Transactional(readOnly = true)
    public List<ClaimBlacklist> listBlacklists() {
        return repository.findByTenant(tenantContext.getCurrentTenantId());
    }
}
