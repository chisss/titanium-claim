package com.titanium.claim.application.orchestration.issuance.validator;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.titanium.claim.application.model.issuance.CreateClaimRequest;
import com.titanium.claim.service.ClaimService;

import lombok.RequiredArgsConstructor;

/**
 * 报案金额校验器（校验链第 1 环，纯领域服务判定）
 * <p>
 * 委托 {@link ClaimService#validateClaimAmount}（domain/service 纯领域规则，无 Port 无命令）判定金额合法，
 * 非法抛 {@code InvalidClaimAmountException}。
 * </p>
 */
@Component
@Order(1)
@RequiredArgsConstructor
public class ClaimAmountValidator implements ClaimRegistrationValidator {

    private final ClaimService claimService;

    @Override
    public void validate(CreateClaimRequest request) {
        claimService.validateClaimAmount(request.getClaimAmount());
    }
}
