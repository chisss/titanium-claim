package com.titanium.claim.application.orchestration.assessment;

import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Component;

import com.titanium.claim.application.model.settlement.SettleDeathBenefitRequest;
import com.titanium.claim.command.SettleDeathBenefitCommand;
import com.titanium.claim.common.context.TenantContext;
import com.titanium.claim.common.exception.BenefitCalculationException;
import com.titanium.claim.common.exception.BusinessException;
import com.titanium.claim.common.exception.PolicyNotActiveException;
import com.titanium.claim.port.policy.BeneficiaryInfo;
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
 * 身故给付结算的同步命令式编排（CLAIM-2 + CLAIM-4）：<b>受益人核验 → Port 取基本保额 →
 * 值对象精算分配 → 发命令</b>。受益人须登记于保单受益人主数据（拒绝未知受益人），
 * 分配顺序按主数据受益顺位（第一顺位优先）；给付金额由系统按条款精算
 * （定额给付 = 保单基本保额，来源规则 {@code BASIC_SUM_INSURED}），不再信任 HTTP 透传金额。
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
     * 身故给付结算：受益人主数据核验 → 取保单基本保额精算给付总额并按顺位份额分配 → 发命令。
     *
     * @param claimId 赔案ID
     * @param request 身故给付结算入参（不含金额，金额由系统精算）
     */
    public void settleDeathBenefit(String claimId, SettleDeathBenefitRequest request) {
        String tenantId = tenantContext.getCurrentTenantId();
        // 1. Port 取数：保单须有效且携带基本保额（定额给付精算依据）
        PolicyInfo policy = policyServicePort.getPolicy(request.getPolicyId(), tenantId);
        if (policy == null || !"ACTIVE".equals(policy.statusCode())) {
            log.error("[身故给付编排] 保单无效或不存在, policyId={}, status={}", request.getPolicyId(),
                    policy == null ? null : policy.statusCode());
            throw new PolicyNotActiveException();
        }
        if (policy.basicSumInsured() == null) {
            throw new BenefitCalculationException(ClaimErrorCode.CLAIM_BENEFIT_AMOUNT_INVALID,
                    "保单基本保额缺失，无法精算身故给付: " + request.getPolicyId());
        }

        // 2. 受益人核验（CLAIM-4）：请求受益人须登记于保单受益人主数据，按顺位分配（第一顺位优先）
        List<BeneficiaryInfo> masterBeneficiaries = policyServicePort.fetchBeneficiaries(request.getPolicyId(),
                tenantId);
        List<BenefitCalculation.BeneficiaryShareSpec> specs = buildShareSpecs(request, masterBeneficiaries);

        // 3. 值对象精算：给付总额 = 基本保额，按受益人比例分配（比例之和=1 与份额守恒由值对象守护）
        BenefitCalculation calculation = BenefitCalculation.ofBasicSumInsured(policy.basicSumInsured(), specs);

        // 4. 装配身故证据并发命令
        DeathClaimEvidence evidence = new DeathClaimEvidence(request.getDeathCertificateNo(), request.getDeathDate(),
                request.getDeathCause(), request.isHouseholdCancelled(), request.getBeneficiaryProofNo(),
                LocalDateTime.now());
        SettleDeathBenefitCommand command = new SettleDeathBenefitCommand(ClaimId.of(claimId), evidence,
                calculation, ClaimEnum.PayoutMethod.fromCode(request.getPayoutMethod()), request.getConclusion());
        commandGateway.sendAndWait(command);

        log.info("[身故给付编排] 给付命令已发送, claimId={}, policyId={}, totalBenefit={}", claimId,
                request.getPolicyId(), calculation.totalBenefit());
    }

    /**
     * 受益人核验与份额规格装配：请求受益人必须在主数据中（拒绝未知受益人），
     * 并按主数据受益顺位（orderNo 升序）排序后装配精算规格。
     */
    private List<BenefitCalculation.BeneficiaryShareSpec> buildShareSpecs(SettleDeathBenefitRequest request,
                                                                          List<BeneficiaryInfo> masterBeneficiaries) {
        if (request.getShares() == null || request.getShares().isEmpty()) {
            log.error("[身故给付编排] 身故给付未指定受益人, policyId={}", request.getPolicyId());
            throw new BusinessException(ClaimErrorCode.CLAIM_BENEFICIARY_INVALID, "身故给付必须指定受益人");
        }
        if (masterBeneficiaries.isEmpty()) {
            log.error("[身故给付编排] 保单受益人主数据为空, policyId={}", request.getPolicyId());
            throw new BusinessException(ClaimErrorCode.CLAIM_BENEFICIARY_INVALID, "保单受益人主数据为空");
        }
        // 请求受益人逐个比对主数据：未知受益人直接拒绝
        for (SettleDeathBenefitRequest.BeneficiaryShare share : request.getShares()) {
            boolean known = masterBeneficiaries.stream()
                    .anyMatch(master -> master.beneficiaryId().equals(share.getBeneficiaryId()));
            if (!known) {
                log.error("[身故给付编排] 拒绝未知受益人, policyId={}, beneficiaryId={}", request.getPolicyId(),
                        share.getBeneficiaryId());
                throw new BusinessException(ClaimErrorCode.CLAIM_BENEFICIARY_INVALID,
                        "受益人不在保单受益人主数据中: " + share.getBeneficiaryId());
            }
        }
        // 按主数据顺位排序（第一顺位优先分配）
        List<SettleDeathBenefitRequest.BeneficiaryShare> orderedShares = request.getShares().stream()
                .sorted(java.util.Comparator.comparingInt(share -> orderOf(share.getBeneficiaryId(),
                        masterBeneficiaries)))
                .toList();
        return orderedShares.stream()
                .map(share -> new BenefitCalculation.BeneficiaryShareSpec(share.getBeneficiaryId(),
                        share.getBeneficiaryName(), share.getBenefitRatio()))
                .toList();
    }

    /**
     * 取受益人在主数据中的顺位（未登记顺位时排最末）
     */
    private int orderOf(String beneficiaryId, List<BeneficiaryInfo> masterBeneficiaries) {
        return masterBeneficiaries.stream()
                .filter(master -> master.beneficiaryId().equals(beneficiaryId))
                .map(BeneficiaryInfo::orderNo)
                .filter(orderNo -> orderNo != null)
                .findFirst()
                .orElse(Integer.MAX_VALUE);
    }
}
