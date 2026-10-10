package com.titanium.claim.application.query;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.claim.aggregate.ClaimHospitalNetwork;
import com.titanium.claim.aggregate.ClaimPayoutRule;
import com.titanium.claim.application.model.assessment.ReimbursementSettlementRequest;
import com.titanium.claim.repository.ClaimHospitalNetworkRepository;
import com.titanium.claim.repository.ClaimPayoutRuleRepository;
import com.titanium.claim.service.ReimbursementAdjustmentService;
import com.titanium.claim.valueobject.ReimbursementAdjustmentRequest;
import com.titanium.claim.valueobject.ReimbursementAdjustmentRequest.ReimbursementAdjustmentResult;
import com.titanium.common.context.RequestContextHolder;
import com.titanium.metadata.errorcode.ClaimErrorCode;
import com.titanium.metadata.exception.DomainException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 报销理算查询服务（application 读入口，健康险/宠物险）
 * <p>
 * 「取数 → 调领域服务拿决策」编排（§3.4.4）：按配置读取优先级（租户覆盖 → 平台默认）查赔付规则，
 * 按医院名查医院网络台账（空=非定点），交 {@link ReimbursementAdjustmentService} 纯领域计算。
 * 读入口只表达「要算什么」，比例裁决与金额公式均下沉领域层。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReimbursementAdjustmentQueryService {

    private final ClaimPayoutRuleRepository     payoutRuleRepository;
    private final ClaimHospitalNetworkRepository hospitalNetworkRepository;
    private final ReimbursementAdjustmentService adjustmentService;

    /**
     * 报销理算：定点资格校验 + 赔付比例裁决 + 金额理算。
     *
     * @param request 理算入参（险种线/理赔类型/出险医院/合规费用）
     * @return 理算结果（应付金额/实际比例/结算渠道）
     */
    public ReimbursementAdjustmentResult adjust(ReimbursementSettlementRequest request) {
        String tenantId = RequestContextHolder.getTenantId();
        // 1. 取赔付规则：租户覆盖优先，回落平台默认模板
        ClaimPayoutRule rule = payoutRuleRepository
                .findByBusinessKey(tenantId, request.getInsuranceLine(), request.getClaimType())
                .or(() -> payoutRuleRepository.findPlatformDefault(request.getInsuranceLine(),
                        request.getClaimType()))
                .orElseThrow(() -> new DomainException(ClaimErrorCode.CLAIM_CONFIG_NOT_FOUND,
                        "赔付规则不存在: " + request.getInsuranceLine() + "/" + request.getClaimType()));

        // 2. 取医院台账：医院名为空或不在台账 = 非定点（台账非 ACTIVE 由领域服务按非定点裁决）
        ClaimHospitalNetwork hospital = null;
        if (request.getHospitalName() != null && !request.getHospitalName().isBlank()) {
            hospital = hospitalNetworkRepository.findByName(tenantId, request.getHospitalName()).orElse(null);
        }

        // 3. 领域服务纯计算
        // 🔴 社保身份（G12/g12-03）暂不传：取数优先级（产品配置/赔付规则配置/保单快照）待 R-2 业务确认，
        //    届时经既有出单快照路径取得后填入。规则未配置社保分档时该入参无意义（既有行为完全不变）；
        //    配置了分档而缺身份由领域服务显式拒绝（fail-closed），不会静默按高档赔付
        ReimbursementAdjustmentResult result = adjustmentService.adjust(
                new ReimbursementAdjustmentRequest(request.getEligibleExpense(), rule, hospital));
        log.info("[报销理算] tenantId={}, line={}, type={}, designated={}, payable={}", tenantId,
                request.getInsuranceLine(), request.getClaimType(), result.settlementChannel(),
                result.calculation().payableAmount());
        return result;
    }
}
