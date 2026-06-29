package com.titanium.claim.application.service;

import org.springframework.stereotype.Service;

import com.titanium.policy.api.PolicyApi;
import com.titanium.policy.api.dto.PolicyDTO;
import com.titanium.policy.api.response.ApiResponse;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单服务客户端
 * 用于调用保单系统的API
 */
@Slf4j
@Service
public class PolicyService {

    @Resource
    private PolicyApi policyApi;

    /**
     * 获取保单详情
     */
    public PolicyDTO getPolicy(String policyId, String tenantId) {
        log.info("获取保单详情, policyId={}, tenantId={}", policyId, tenantId);
        ApiResponse<PolicyDTO> response = policyApi.getPolicy(policyId, tenantId);
        if (response.isSuccess()) {
            return response.getData();
        } else {
            log.error("获取保单详情失败, policyId={}, error={}", policyId, response.getMessage());
            throw new RuntimeException("获取保单详情失败: " + response.getMessage());
        }
    }
}
