package com.titanium.claim.application.orchestration.assessment;

import java.math.BigDecimal;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Component;

import com.titanium.claim.application.model.assessment.ReimbursementSettlementRequest;
import com.titanium.claim.application.model.settlement.SettleReimbursementRequest;
import com.titanium.claim.application.query.ReimbursementAdjustmentQueryService;
import com.titanium.claim.command.SettleClaimCommand;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.ReimbursementAdjustmentRequest.ReimbursementAdjustmentResult;
import com.titanium.metadata.enums.claim.ClaimEnum;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 报销理算结算编排器（application/orchestration/assessment，健康险/宠物险）
 * <p>
 * 补上「报销理算 → 结算」缺失的一段：<b>取数理算（按赔付规则与医院台账精算应付金额）→
 * 发 {@link SettleClaimCommand} 按该金额结算</b>。理算链此前止于试算（只返回金额、不产出命令），
 * 核赔员算出的金额无法进入结算链路。
 * </p>
 * <p>
 * 🔴 <b>金额来源唯一（m16-1905）</b>：应付金额复用 {@link ReimbursementAdjustmentQueryService}
 * 的理算能力（同一取数 + 同一 {@code ReimbursementAdjustmentService} 领域计算），
 * 使「试算看到的金额」与「结算用的金额」严格同源——编排器<b>不复制</b>规则解析逻辑，
 * 也不接收前端透传金额。若案件在案定损，聚合 {@code Claim.resolveSettledAmount} 以定损核定额为
 * 唯一权威，本编排器传入的理算金额与核定额不等即被拒（失败关闭，不静默改写金额）。
 * </p>
 * <p>
 * 🔴 <b>不代核赔通过（与 {@link QuickPayOrchestrator} 的差异）</b>：本编排器只发结算命令，
 * 不推进状态。核赔通过是<b>人的决定</b>，有独立入口与合法流转守卫
 * （{@code PENDING → PROCESSING → APPROVED}）——案件须先 APPROVED 才可结算，
 * 该前置由聚合校验。快赔自带 APPROVED 是因为它是<b>无人干预的自动通道</b>，语义不同，不照搬。
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReimbursementSettlementOrchestrator {

    private final ReimbursementAdjustmentQueryService reimbursementAdjustmentQueryService;
    private final CommandGateway                  commandGateway;

    /**
     * 报销理算结算：理算应付金额 → 按该金额结算。
     *
     * @param claimId 理赔案件ID（须已核赔通过 APPROVED）
     * @param request 理算四要素 + 给付参数
     */
    public void settle(String claimId, SettleReimbursementRequest request) {
        ReimbursementAdjustmentResult result =
                reimbursementAdjustmentQueryService.adjust(toAdjustmentRequest(request));
        BigDecimal payableAmount = result.calculation().payableAmount();
        commandGateway.sendAndWait(new SettleClaimCommand(ClaimId.of(claimId), payableAmount,
                ClaimEnum.PayoutMethod.fromCode(request.getPayoutMethod()), request.getPayeeAccount(),
                request.getConclusion()));
        log.info("[报销结算编排] 按理算金额结算, claimId={}, channel={}, ratio={}, payable={}", claimId,
                result.settlementChannel(), result.payoutRatioUsed(), payableAmount);
    }

    /**
     * 结算入参 → 理算入参（剥掉给付参数：理算只认险种线/理赔类型/医院/合规费用）。
     */
    private ReimbursementSettlementRequest toAdjustmentRequest(SettleReimbursementRequest request) {
        ReimbursementSettlementRequest adjustment = new ReimbursementSettlementRequest();
        adjustment.setInsuranceLine(request.getInsuranceLine());
        adjustment.setClaimType(request.getClaimType());
        adjustment.setHospitalName(request.getHospitalName());
        adjustment.setEligibleExpense(request.getEligibleExpense());
        return adjustment;
    }
}
