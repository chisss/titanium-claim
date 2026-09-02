package com.titanium.claim.application.query.config;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.claim.aggregate.ClaimQuickPayRule;
import com.titanium.claim.common.context.TenantContext;
import com.titanium.claim.common.exception.BusinessException;
import com.titanium.claim.repository.ClaimQuickPayRuleRepository;
import com.titanium.metadata.errorcode.ClaimErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 快赔规则配置查询服务（application 层配置子域读入口）
 * <p>
 * 配置子域为状态存储（无读模型投影），读请求直接查写侧仓储（§3.4.9 ⑥ 领域内部查询走写模型聚合），
 * 返回领域聚合由 web 层 MapStruct 组装 VO。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class QuickPayRuleConfigQueryService {

    private final ClaimQuickPayRuleRepository repository;
    private final TenantContext               tenantContext;

    /**
     * 按规则ID查询
     */
    @Transactional(readOnly = true)
    public ClaimQuickPayRule getQuickPayRule(String ruleId) {
        return repository.findById(tenantContext.getCurrentTenantId(), ruleId)
                .orElseThrow(() -> new BusinessException(ClaimErrorCode.CLAIM_CONFIG_NOT_FOUND,
                        "快赔规则不存在: " + ruleId));
    }

    /**
     * 查询租户下全部规则
     */
    @Transactional(readOnly = true)
    public List<ClaimQuickPayRule> listQuickPayRules() {
        return repository.findByTenant(tenantContext.getCurrentTenantId());
    }
}
