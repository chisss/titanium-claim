package com.titanium.claim.application.orchestration.issuance;

import java.util.List;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Component;

import com.titanium.claim.application.model.issuance.CreateClaimRequest;
import com.titanium.claim.application.orchestration.issuance.validator.ClaimRegistrationValidator;
import com.titanium.claim.command.CreateClaimCommand;
import com.titanium.claim.service.ClaimService;
import com.titanium.claim.valueobject.ClaimAmount;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.CustomerId;
import com.titanium.claim.valueobject.PolicyId;
import com.titanium.metadata.enums.claim.ClaimEnum;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 报案校验链编排器（跨聚合取数编排，application/orchestration/issuance）
 * <p>
 * 报案用例的同步命令式编排：<b>校验链（领域服务纯判定 + Port 取数）→ 生成理赔 ID/编号 → 发命令</b>。
 * 校验链由 {@link ClaimRegistrationValidator} 策略接口构成（Spring 按 {@code @Order} 注入，
 * 金额校验 → 保单有效性校验），新增校验（如条款责任校验、反欺诈警示）只需追加校验器，符合开闭原则。
 * 保单校验是跨微服务取数（{@code PolicyServicePort}），属 application 编排职责而非领域服务（§3.4.4 三无判据）。
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClaimRegistrationOrchestrator {

    private final List<ClaimRegistrationValidator> validators;
    private final ClaimService                     claimService;
    private final CommandGateway                   commandGateway;

    /**
     * 报案登记：校验链 → 生成理赔 ID 与理赔编号 → 装配并发送 {@link CreateClaimCommand}。
     *
     * @param request 报案入参（web/api 经 WebMapper 收敛的应用层入参）
     * @return 新理赔案件 ID
     */
    public String registerClaim(CreateClaimRequest request) {
        // 1. 校验链：金额（领域服务判定）→ 保单有效性（Port 取数），任一失败抛领域异常中断报案
        validators.forEach(validator -> validator.validate(request));

        // 2. 生成理赔 ID 与理赔编号
        ClaimId claimId = ClaimId.generate();
        String claimNumber = claimService.generateClaimNumber();

        // 3. 装配并发命令
        CreateClaimCommand command = new CreateClaimCommand(
                claimId,
                CustomerId.of(request.getCustomerId()),
                PolicyId.of(request.getPolicyId()),
                claimNumber,
                ClaimEnum.ClaimType.fromCode(request.getClaimType()),
                request.getIncidentDate(),
                request.getIncidentDescription(),
                ClaimAmount.of(request.getClaimAmount()));
        commandGateway.sendAndWait(command);

        log.info("[报案编排] 理赔创建命令已发送, claimId={}, claimNumber={}", claimId.value(), claimNumber);
        return claimId.value();
    }
}
