package com.titanium.claim.application.orchestration.issuance.validator;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.titanium.claim.application.model.issuance.CreateClaimRequest;
import com.titanium.claim.common.context.TenantContext;
import com.titanium.claim.common.exception.PolicyNotActiveException;
import com.titanium.claim.port.policy.PolicyInfo;
import com.titanium.claim.port.policy.PolicyServicePort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单有效性校验器（校验链第 2 环，跨微服务 Port 取数）
 * <p>
 * 经 {@link PolicyServicePort} 取保单信息（infrastructure Adapter 走 Feign 保单域），校验状态为 {@code ACTIVE}；
 * 保单不存在或非 ACTIVE 一律抛 {@link PolicyNotActiveException} 中断报案。租户 ID 取 {@link TenantContext}
 * （Web 拦截器写入的真实租户，多租户贯穿）。
 * </p>
 */
@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class PolicyActiveValidator implements ClaimRegistrationValidator {

    private final PolicyServicePort policyServicePort;
    private final TenantContext     tenantContext;

    @Override
    public void validate(CreateClaimRequest request) {
        PolicyInfo policy = policyServicePort.getPolicy(request.getPolicyId(), tenantContext.getCurrentTenantId());
        String statusCode = policy == null ? null : policy.statusCode();
        if (!"ACTIVE".equals(statusCode)) {
            log.error("[报案校验] 保单验证失败, policyId={}, status={}", request.getPolicyId(), statusCode);
            throw new PolicyNotActiveException();
        }
        log.info("[报案校验] 保单验证通过, policyId={}, status={}", request.getPolicyId(), statusCode);
    }
}
