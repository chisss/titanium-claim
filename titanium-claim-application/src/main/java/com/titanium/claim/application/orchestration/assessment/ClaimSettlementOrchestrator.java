package com.titanium.claim.application.orchestration.assessment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Component;

import com.titanium.claim.application.model.settlement.SettleDeathBenefitRequest;
import com.titanium.claim.application.model.settlement.SettleDisabilityBenefitRequest;
import com.titanium.claim.command.SettleDeathBenefitCommand;
import com.titanium.claim.command.SettleDisabilityBenefitCommand;
import com.titanium.claim.common.context.TenantContext;
import com.titanium.claim.common.exception.BenefitCalculationException;
import com.titanium.claim.common.exception.BusinessException;
import com.titanium.claim.common.exception.PolicyNotActiveException;
import com.titanium.claim.port.customer.CustomerServicePort;
import com.titanium.claim.port.policy.BeneficiaryInfo;
import com.titanium.claim.port.policy.PolicyInfo;
import com.titanium.claim.port.policy.PolicyServicePort;
import com.titanium.claim.valueobject.BenefitCalculation;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.DeathClaimEvidence;
import com.titanium.claim.valueobject.DisabilityClaimEvidence;
import com.titanium.metadata.enums.claim.ClaimEnum;
import com.titanium.metadata.enums.customer.CustomerEnum;
import com.titanium.metadata.errorcode.ClaimErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 理算/给付编排器（application/orchestration/assessment）
 * <p>
 * 身故给付结算的同步命令式编排（CLAIM-2 + CLAIM-4）：<b>受益人核验（保单主数据登记 + 客户身份取证）→
 * Port 取基本保额 → 值对象精算分配 → 发命令</b>。受益人须登记于保单受益人主数据（拒绝未知受益人），
 * 且其客户身份在客户域须存在且状态有效（受益人不存在/已销户时阻断给付）；分配顺序按主数据受益顺位
 * （第一顺位优先）；给付金额由系统按条款精算（定额给付 = 保单基本保额，来源规则 {@code BASIC_SUM_INSURED}），
 * 不再信任 HTTP 透传金额。
 * 取数是跨微服务 Port 调用、发命令是编排职责，均属 application（非领域服务，§3.4.4 三无判据）。
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClaimSettlementOrchestrator {

    private final PolicyServicePort   policyServicePort;
    private final CustomerServicePort customerServicePort;
    private final TenantContext       tenantContext;
    private final CommandGateway      commandGateway;

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
        if (policy == null || !policy.isEffective()) {
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
        List<ShareInput> requestShares = request.getShares() == null ? List.of()
                : request.getShares().stream().map(share -> new ShareInput(share.getBeneficiaryId(),
                        share.getBeneficiaryName(), share.getBenefitRatio())).toList();
        List<BenefitCalculation.BeneficiaryShareSpec> specs = buildShareSpecs(request.getPolicyId(),
                requestShares, masterBeneficiaries, tenantId);

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
     * 全残给付结算（CLAIM-6）：受益人核验 → Port 取保单基本保额/账户价值 →
     * 值对象精算分配（账户价值与基本保额孰高）→ 发命令。与身故给付同构：
     * 给付后保单责任终止（被保险人全残，下游据事件派发保单终止）。
     */
    public void settleDisabilityBenefit(String claimId, SettleDisabilityBenefitRequest request) {
        String tenantId = tenantContext.getCurrentTenantId();
        // 1. Port 取数：保单须有效且携带基本保额（全残给付精算依据）
        PolicyInfo policy = policyServicePort.getPolicy(request.getPolicyId(), tenantId);
        if (policy == null || !policy.isEffective()) {
            log.error("[全残给付编排] 保单无效或不存在, policyId={}, status={}", request.getPolicyId(),
                    policy == null ? null : policy.statusCode());
            throw new PolicyNotActiveException();
        }
        if (policy.basicSumInsured() == null) {
            throw new BenefitCalculationException(ClaimErrorCode.CLAIM_BENEFIT_AMOUNT_INVALID,
                    "保单基本保额缺失，无法精算全残给付: " + request.getPolicyId());
        }

        // 2. 受益人核验（CLAIM-4）：请求受益人须登记于保单受益人主数据，按顺位分配（第一顺位优先）
        List<BeneficiaryInfo> masterBeneficiaries = policyServicePort.fetchBeneficiaries(request.getPolicyId(),
                tenantId);
        List<ShareInput> requestShares = request.getShares() == null ? List.of()
                : request.getShares().stream().map(share -> new ShareInput(share.getBeneficiaryId(),
                        share.getBeneficiaryName(), share.getBenefitRatio())).toList();
        List<BenefitCalculation.BeneficiaryShareSpec> specs = buildShareSpecs(request.getPolicyId(),
                requestShares, masterBeneficiaries, tenantId);

        // 3. 值对象精算：给付总额 = max(账户价值, 基本保额)（账户价值未取到回落基本保额），按受益人比例分配
        BenefitCalculation calculation = BenefitCalculation.ofAccountValueMax(policy.cashValue(),
                policy.basicSumInsured(), specs);

        // 4. 装配全残证据并发命令
        DisabilityClaimEvidence evidence = new DisabilityClaimEvidence(request.getDisabilityCertificateNo(),
                request.getDisabilityGrade(), request.getAssessmentDate(), request.getAssessmentAgency(),
                request.getBeneficiaryProofNo(), LocalDateTime.now());
        SettleDisabilityBenefitCommand command = new SettleDisabilityBenefitCommand(ClaimId.of(claimId), evidence,
                calculation, ClaimEnum.PayoutMethod.fromCode(request.getPayoutMethod()), request.getConclusion());
        commandGateway.sendAndWait(command);

        log.info("[全残给付编排] 给付命令已发送, claimId={}, policyId={}, totalBenefit={}", claimId,
                request.getPolicyId(), calculation.totalBenefit());
    }

    /**
     * 受益人核验与份额规格装配：请求受益人必须在主数据中（拒绝未知受益人），
     * 其客户身份须在客户域存在且状态有效（CLAIM-4 第二道，见
     * {@link #verifyBeneficiaryCustomers(String, List, List, String)}），
     * 并按主数据受益顺位（orderNo 升序）排序后装配精算规格。
     * <p>
     * 身故给付与全残给付共用本方法，两道核验对两条给付链路同时生效——不因给付形态不同而松紧不一。
     * </p>
     */
    private List<BenefitCalculation.BeneficiaryShareSpec> buildShareSpecs(String policyId,
                                                                          List<ShareInput> requestShares,
                                                                          List<BeneficiaryInfo> masterBeneficiaries,
                                                                          String tenantId) {
        if (requestShares == null || requestShares.isEmpty()) {
            log.error("[给付编排] 给付未指定受益人, policyId={}", policyId);
            throw new BusinessException(ClaimErrorCode.CLAIM_BENEFICIARY_INVALID, "给付必须指定受益人");
        }
        if (masterBeneficiaries.isEmpty()) {
            log.error("[给付编排] 保单受益人主数据为空, policyId={}", policyId);
            throw new BusinessException(ClaimErrorCode.CLAIM_BENEFICIARY_INVALID, "保单受益人主数据为空");
        }
        // 请求受益人逐个比对主数据：未知受益人直接拒绝
        for (ShareInput share : requestShares) {
            boolean known = masterBeneficiaries.stream()
                    .anyMatch(master -> master.beneficiaryId().equals(share.beneficiaryId()));
            if (!known) {
                log.error("[给付编排] 拒绝未知受益人, policyId={}, beneficiaryId={}", policyId,
                        share.beneficiaryId());
                throw new BusinessException(ClaimErrorCode.CLAIM_BENEFICIARY_INVALID,
                        "受益人不在保单受益人主数据中: " + share.beneficiaryId());
            }
        }
        // 第二道核验：受益人客户身份（客户域存在且状态有效），拒绝「受益人不存在/已销户」的给付
        verifyBeneficiaryCustomers(policyId, requestShares, masterBeneficiaries, tenantId);
        // 按主数据顺位排序（第一顺位优先分配）
        List<ShareInput> orderedShares = requestShares.stream()
                .sorted(java.util.Comparator.comparingInt(share -> orderOf(share.beneficiaryId(),
                        masterBeneficiaries)))
                .toList();
        return orderedShares.stream()
                .map(share -> new BenefitCalculation.BeneficiaryShareSpec(share.beneficiaryId(),
                        share.beneficiaryName(), share.benefitRatio()))
                .toList();
    }

    /**
     * 受益人客户身份核验（CLAIM-4 第二道）：逐受益人回客户域取证，客户不存在或状态非有效即阻断给付
     * <p>
     * 与第一道「登记于保单受益人主数据」互补：主数据证明<b>受益权成立</b>，客户域取证证明
     * <b>受益主体真实且有效</b>（防已销户/虚构受益人冒领）。三类违约一律抛异常中断结算，
     * <b>不得只记日志放行</b>——核验的意义就在于拦得住：
     * <ul>
     *   <li>受益人未关联客户主数据（customerId 缺失）→ 拒绝</li>
     *   <li>客户主数据中不存在 → 拒绝</li>
     *   <li>客户状态非 {@code ACTIVE}（不活跃/已暂停/已关闭）→ 拒绝</li>
     * </ul>
     * 客户域调用失败（Feign 异常）原样上抛，不通融为「查不到」：取不到证据即不得给付。
     * </p>
     */
    private void verifyBeneficiaryCustomers(String policyId, List<ShareInput> requestShares,
                                            List<BeneficiaryInfo> masterBeneficiaries, String tenantId) {
        for (ShareInput share : requestShares) {
            String customerId = customerIdOf(share.beneficiaryId(), masterBeneficiaries);
            if (customerId == null || customerId.isBlank()) {
                log.error("[给付编排] 受益人未关联客户主数据, policyId={}, beneficiaryId={}", policyId,
                        share.beneficiaryId());
                throw new BusinessException(ClaimErrorCode.CLAIM_BENEFICIARY_CUSTOMER_INVALID,
                        "受益人未关联客户主数据，无法核验身份: " + share.beneficiaryId());
            }
            CustomerServicePort.CustomerInfo customer = customerServicePort.getCustomer(customerId, tenantId);
            if (customer == null) {
                log.error("[给付编排] 受益人在客户域不存在, policyId={}, beneficiaryId={}, customerId={}", policyId,
                        share.beneficiaryId(), customerId);
                throw new BusinessException(ClaimErrorCode.CUSTOMER_NOT_FOUND,
                        "受益人在客户主数据中不存在: " + customerId);
            }
            if (!CustomerEnum.CustomerStatus.ACTIVE.getCode().equals(customer.statusCode())) {
                log.error("[给付编排] 受益人客户状态无效, policyId={}, customerId={}, status={}", policyId, customerId,
                        customer.statusCode());
                throw new BusinessException(ClaimErrorCode.CLAIM_BENEFICIARY_CUSTOMER_INVALID,
                        "受益人客户状态非有效: " + customerId + "，当前状态 " + customer.statusCode());
            }
        }
    }

    /**
     * 取受益人在保单受益人主数据中登记的客户ID（未登记返回 null）
     * <p>
     * 中间映射会产出 null（未关联客户的受益人），须先滤掉再 {@code findFirst()}——
     * {@code Optional.findFirst()} 遇到 null 元素直接抛 NPE，会把「未关联客户」这一业务判断变成运行期崩溃。
     * </p>
     */
    private String customerIdOf(String beneficiaryId, List<BeneficiaryInfo> masterBeneficiaries) {
        return masterBeneficiaries.stream()
                .filter(master -> master.beneficiaryId().equals(beneficiaryId))
                .map(BeneficiaryInfo::customerId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
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

    /**
     * 受益人份额输入（身故/全残给付请求份额的内部统一视图，供核验与规格装配复用）
     */
    private record ShareInput(String beneficiaryId, String beneficiaryName, java.math.BigDecimal benefitRatio) {
    }
}
