package com.titanium.claim.application.service;

import org.springframework.stereotype.Service;

import com.titanium.claim.common.exception.BusinessException;
import com.titanium.metadata.errorcode.ClaimErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.PolicyApi;
import com.titanium.policy.api.response.PolicyResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单服务客户端
 * <p>
 * 用于调用保单系统的API。构造器注入 {@link PolicyApi}（禁用字段注入）。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyService {

    private final PolicyApi policyApi;

    /**
     * 获取保单详情
     */
    public PolicyResponse getPolicy(String policyId, String tenantId) {
        log.info("获取保单详情, policyId={}, tenantId={}", policyId, tenantId);
        ApiResponse<PolicyResponse> response = policyApi.getPolicy(policyId, tenantId);
        if (response.isSuccess()) {
            return response.getData();
        } else {
            log.error("获取保单详情失败, policyId={}, error={}", policyId, response.getMessage());
            throw new BusinessException(ClaimErrorCode.POLICY_DETAIL_QUERY_FAILED,
                    "获取保单详情失败: " + response.getMessage());
        }
    }
}
