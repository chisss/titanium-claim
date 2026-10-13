package com.titanium.claim.application.query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.claim.aggregate.ClaimHospitalNetwork;
import com.titanium.claim.aggregate.ClaimPayoutRule;
import com.titanium.claim.application.model.assessment.ReimbursementSettlementRequest;
import com.titanium.claim.common.enums.ClaimStatus;
import com.titanium.claim.query.repository.ClaimViewRepository;
import com.titanium.claim.query.view.ClaimView;
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
 * <p>
 * 🔴 <b>年度封顶取数（G12/g12-04b、AC-04）</b>：赔付规则配置了年度限额且入参携带案件ID 时，
 * 按案件读模型（保单/标的/出险日）计算本自然年已结算累计并传入领域服务压减。口径：自然年 /
 * 保单内标的（subject 归一化）/ 出险日锚定 / 只计 {@code PAID}+{@code CLOSED} 终态 / 排除当前案件。
 * 试算与结算共用本服务（同源，m16-1905 金额同源纪律），两链路年度口径一致。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReimbursementAdjustmentQueryService {

    private final ClaimPayoutRuleRepository     payoutRuleRepository;
    private final ClaimHospitalNetworkRepository hospitalNetworkRepository;
    private final ClaimViewRepository           claimViewRepository;
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

        // 3. 年度封顶上下文取数（G12/g12-04b）：携带案件ID 时按案件读模型定自然年并累计
        BigDecimal annualAccumulated = resolveAnnualAccumulated(tenantId, request, rule);

        // 4. 领域服务纯计算
        // 🔴 社保身份（G12/g12-03）暂不传：取数优先级（产品配置/赔付规则配置/保单快照）待 R-2 业务确认，
        //    届时经既有出单快照路径取得后填入。规则未配置社保分档时该入参无意义（既有行为完全不变）；
        //    配置了分档而缺身份由领域服务显式拒绝（fail-closed），不会静默按高档赔付
        ReimbursementAdjustmentResult result = adjustmentService.adjust(
                new ReimbursementAdjustmentRequest(request.getEligibleExpense(), rule, hospital, null,
                        annualAccumulated));
        log.info("[报销理算] tenantId={}, line={}, type={}, designated={}, payable={}", tenantId,
                request.getInsuranceLine(), request.getClaimType(), result.settlementChannel(),
                result.calculation().payableAmount());
        return result;
    }

    /**
     * 年度封顶累计取数（G12/g12-04b、AC-04）
     * <p>
     * 口径（2026-10-13 推荐裁示，与 {@code ClaimViewRepository#sumSettledAmountByAnnualContext} 一致）：
     * 自然年 / 保单内标的（subject 归一化）/ 出险日锚定 / 只计 {@code PAID}+{@code CLOSED} 终态 /
     * 排除当前案件（重复试算或结算重放防自我计数）。
     * </p>
     * <p>
     * 取数路径：规则未配年度限额 或 案件ID 为空（无上下文试算）⇒ 返回 null（不压减，日志标注，非静默）；
     * 案件ID 非空 ⇒ 按案件读模型取保单/标的/出险日，定自然年半开区间后累计求和。
     * 🔴 案件ID 非空而案件读模型不存在 = 上下文不实，显式拒绝（fail-closed，不按 0 累计放行）。
     * </p>
     *
     * @param tenantId 租户ID
     * @param request 理算入参（案件ID 可选）
     * @param rule 赔付规则（annualLimit 未配置时无需取数）
     * @return 本年度累计已赔付金额；无年度上下文时返回 null
     */
    private BigDecimal resolveAnnualAccumulated(String tenantId, ReimbursementSettlementRequest request,
                                                ClaimPayoutRule rule) {
        if (rule.getAnnualLimit() == null) {
            return null;
        }
        String claimId = request.getClaimId();
        if (claimId == null || claimId.isBlank()) {
            log.info("[报销理算] 未携带案件ID，未计入年度累计（单次公式口径）: line={}, type={}",
                    request.getInsuranceLine(), request.getClaimType());
            return null;
        }
        ClaimView claim = claimViewRepository.findByClaimIdAndTenantId(claimId, tenantId)
                .orElseThrow(() -> new DomainException(ClaimErrorCode.CLAIM_NOT_EXIST,
                        "年度封顶取数失败，理赔案件不存在: " + claimId));
        if (claim.getPolicyId() == null || claim.getIncidentDate() == null) {
            log.warn("[报销理算] 案件缺保单ID或出险日期，无法定自然年，未计入年度累计: claimId={}", claimId);
            return null;
        }
        int year = claim.getIncidentDate().getYear();
        LocalDateTime from = LocalDate.of(year, 1, 1).atStartOfDay();
        LocalDateTime to = LocalDate.of(year + 1, 1, 1).atStartOfDay();
        String subjectKey = claim.getSubjectId() == null ? "" : claim.getSubjectId();
        BigDecimal accumulated = claimViewRepository.sumSettledAmountByAnnualContext(tenantId,
                claim.getPolicyId(), subjectKey, from, to, List.of(ClaimStatus.PAID, ClaimStatus.CLOSED),
                claim.getClaimId());
        log.info("[报销理算] 年度累计取数: claimId={}, year={}, subjectKey={}, accumulated={}",
                claimId, year, subjectKey, accumulated);
        return accumulated;
    }
}
