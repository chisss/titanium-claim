package com.titanium.claim.infrastructure.adapter.policy;

import org.springframework.stereotype.Component;

import com.titanium.claim.common.exception.BusinessException;
import com.titanium.claim.port.policy.PolicyInfo;
import com.titanium.claim.port.policy.PolicyServicePort;
import com.titanium.metadata.errorcode.ClaimErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.PolicyApi;
import com.titanium.policy.api.response.PolicyResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单服务 Adapter（对端域：policy）
 * <p>
 * 实现 {@link PolicyServicePort}：调用保单域 {@link PolicyApi} Feign，将下游契约
 * {@link PolicyResponse} 翻译为领域摘要 {@link PolicyInfo}（防腐：领域不依赖对端 api 类型）。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyServiceAdapter implements PolicyServicePort {

    private final PolicyApi policyApi;

    @Override
    public PolicyInfo getPolicy(String policyId, String tenantId) {
        log.info("获取保单详情, policyId={}, tenantId={}", policyId, tenantId);
        ApiResponse<PolicyResponse> response = policyApi.getPolicy(policyId, tenantId);
        if (!response.isSuccess()) {
            log.error("获取保单详情失败, policyId={}, error={}", policyId, response.getMessage());
            throw new BusinessException(ClaimErrorCode.POLICY_DETAIL_QUERY_FAILED,
                    "获取保单详情失败: " + response.getMessage());
        }
        PolicyResponse policy = response.getData();
        if (policy == null) {
            return null;
        }
        return new PolicyInfo(policy.getPolicyId(),
                policy.getStatus() == null ? null : policy.getStatus().getCode());
    }
}
