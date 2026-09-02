package com.titanium.claim.application.orchestration.issuance.validator;

import com.titanium.claim.application.model.issuance.CreateClaimRequest;

/**
 * 报案校验器策略接口（校验链成员）
 * <p>
 * 报案登记前逐项校验：金额校验（领域服务纯判定）、保单有效性校验（Port 取数）等。
 * 新增校验环节只需新增实现并以 {@code @Order} 声明链序，报案编排器零改动（开闭原则）。
 * 校验失败一律抛领域异常中断报案。
 * </p>
 */
public interface ClaimRegistrationValidator {

    /**
     * 执行校验，失败抛领域异常。
     *
     * @param request 报案入参
     */
    void validate(CreateClaimRequest request);
}
