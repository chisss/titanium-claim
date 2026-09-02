package com.titanium.claim.application.orchestration.assessment;

import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Component;

import com.titanium.claim.application.model.settlement.SettleDeathBenefitRequest;
import com.titanium.claim.command.SettleDeathBenefitCommand;
import com.titanium.claim.common.context.TenantContext;
import com.titanium.claim.common.exception.BenefitCalculationException;
import com.titanium.claim.common.exception.PolicyNotActiveException;
import com.titanium.claim.port.policy.PolicyInfo;
import com.titanium.claim.port.policy.PolicyServicePort;
import com.titanium.claim.valueobject.BenefitCalculation;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.DeathClaimEvidence;
import com.titanium.metadata.enums.claim.ClaimEnum;
import com.titanium.metadata.errorcode.ClaimErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 理算/给付编排器（application/orchestration/assessment）
 * <p>
 * 身故给付结算的同步命令式编排（CLAIM-2）：<b>Port 取基本保额 → 值对象精算分配 → 发命令</b>。
 * 给付金额由系统按条款精算（定额给付 = 保单基本保额，来源规则 {@code BASIC_SUM_INSURED}），
 * 不再信任 HTTP 透传金额；受益人应得金额按约定比例分配，尾差调整至最后一位受益人。
 * 取数是跨微服务 Port 调用、发命令是编排职责，均属 application（非领域服务，§3.4.4 三无判据）。
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClaimSettlementOrchestrator {

    private final PolicyServicePort policyServicePort;
    private final TenantContext     tenantContext;
    private final CommandGateway    commandGateway;

    /**
     * 身故给付结算：取保单基本保额精算给付总额并按份额分配，装配身故证据并发命令。
     *
     * @param claimId 赔案ID
     * @param request 身故给付结算入参（不含金额，金额由系统精算）
     */
    public void settleDeathBenefit(String claimId, SettleDeathBenefitRequest request) {
        // 1. Port 取数：保单须有效且携带基本保额（定额给付精算依据）
        PolicyInfo policy = policyServicePort.getPolicy(request.getPolicyId(), tenantContext.getCurrentTenantId());
        if (policy == null || !"ACTIVE".equals(policy.statusCode())) {
            log.error("[身故给付编排] 保单无效或不存在, policyId={}, status={}", request.getPolicyId(),
                    policy == null ? null : policy.statusCode());
            throw new PolicyNotActiveException();
        }
        if (policy.basicSumInsured() == null) {
            throw new BenefitCalculationException(ClaimErrorCode.CLAIM_BENEFIT_AMOUNT_INVALID,
                    "保单基本保额缺失，无法精算身故给付: " + request.getPolicyId());
        }

        // 2. 值对象精算：给付总额 = 基本保额，按受益人比例分配（比例之和=1 与份额守恒由值对象守护）
        List<BenefitCalculation.BeneficiaryShareSpec> specs = request.getShares() == null ? List.of()
                : request.getShares().stream()
                        .map(s -> new BenefitCalculation.BeneficiaryShareSpec(s.getBeneficiaryId(),
                                s.getBeneficiaryName(), s.getBenefitRatio()))
                        .toList();
        BenefitCalculation calculation = BenefitCalculation.ofBasicSumInsured(policy.basicSumInsured(), specs);

        // 3. 装配身故证据并发命令
        DeathClaimEvidence evidence = new DeathClaimEvidence(request.getDeathCertificateNo(), request.getDeathDate(),
                request.getDeathCause(), request.isHouseholdCancelled(), request.getBeneficiaryProofNo(),
                LocalDateTime.now());
        SettleDeathBenefitCommand command = new SettleDeathBenefitCommand(ClaimId.of(claimId), evidence,
                calculation, ClaimEnum.PayoutMethod.fromCode(request.getPayoutMethod()), request.getConclusion());
        commandGateway.sendAndWait(command);

        log.info("[身故给付编排] 给付命令已发送, claimId={}, policyId={}, totalBenefit={}", claimId,
                request.getPolicyId(), calculation.totalBenefit());
    }
}
