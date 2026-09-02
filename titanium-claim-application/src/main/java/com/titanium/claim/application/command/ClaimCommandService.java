package com.titanium.claim.application.command;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.claim.application.model.assessment.FlagClaimAlertRequest;
import com.titanium.claim.application.model.assessment.SubmitLossAssessmentRequest;
import com.titanium.claim.application.model.assessment.SubmitSurveyRequest;
import com.titanium.claim.application.model.issuance.CreateClaimRequest;
import com.titanium.claim.application.model.maintenance.ChangeClaimStatusRequest;
import com.titanium.claim.application.model.maintenance.RejectClaimRequest;
import com.titanium.claim.application.model.maintenance.UpdateClaimRequest;
import com.titanium.claim.application.model.settlement.SettleClaimRequest;
import com.titanium.claim.application.model.settlement.SettleDeathBenefitRequest;
import com.titanium.claim.application.model.settlement.SettleDisabilityBenefitRequest;
import com.titanium.claim.application.orchestration.assessment.ClaimAlertOrchestrator;
import com.titanium.claim.application.orchestration.assessment.ClaimSettlementOrchestrator;
import com.titanium.claim.application.orchestration.assessment.QuickPayOrchestrator;
import com.titanium.claim.application.orchestration.issuance.ClaimRegistrationOrchestrator;
import com.titanium.claim.command.ChangeClaimStatusCommand;
import com.titanium.claim.command.CloseClaimCommand;
import com.titanium.claim.command.FlagClaimAlertCommand;
import com.titanium.claim.command.RejectClaimCommand;
import com.titanium.claim.command.SettleClaimCommand;
import com.titanium.claim.command.SubmitLossAssessmentCommand;
import com.titanium.claim.command.SubmitSurveyCommand;
import com.titanium.claim.command.UpdateClaimCommand;
import com.titanium.claim.common.context.TenantContext;
import com.titanium.claim.common.enums.AlertType;
import com.titanium.claim.common.enums.ClaimStatus;
import com.titanium.claim.common.enums.RejectReason;
import com.titanium.claim.service.ClaimService;
import com.titanium.claim.valueobject.AlertFlag;
import com.titanium.claim.valueobject.ClaimAmount;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.LossAssessment;
import com.titanium.claim.valueobject.Survey;
import com.titanium.metadata.enums.claim.ClaimEnum;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 理赔写入口门面（application 层写用例入口，薄）
 * <p>
 * 单聚合单命令直接 {@code commandGateway.sendAndWait}（KISS）；报案用例（跨源校验链 + 生成理赔 ID/编号）
 * 委托 {@link ClaimRegistrationOrchestrator} 编排（Port 取数 → 领域服务判定 → 发命令）。业务规则在聚合根/领域服务，
 * 本门面不写业务规则。web 层与 api 层只能依赖本包与 {@code application/query}（ArchUnit 固化）。
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClaimCommandService {

    private final CommandGateway               commandGateway;
    private final ClaimService                 claimService;
    private final ClaimRegistrationOrchestrator claimRegistrationOrchestrator;
    private final ClaimSettlementOrchestrator   claimSettlementOrchestrator;
    private final ClaimAlertOrchestrator        claimAlertOrchestrator;
    private final QuickPayOrchestrator           quickPayOrchestrator;
    private final TenantContext                 tenantContext;

    /**
     * 创建理赔案件（报案）：委托报案校验链编排器，返回理赔 ID。
     */
    @Transactional
    public String createClaim(CreateClaimRequest request) {
        String claimId = claimRegistrationOrchestrator.registerClaim(request);
        // 报案后自动风险评分：延迟报案/多次报案判据命中即打标（读模型投影供快赔判据使用）
        claimAlertOrchestrator.scoreAndFlag(claimId, request.getPolicyId(), request.getIncidentDate(),
                LocalDateTime.now(), tenantContext.getCurrentTenantId());
        return claimId;
    }

    @Transactional
    public void updateClaim(String claimId, UpdateClaimRequest request) {
        // 验证理赔金额
        claimService.validateClaimAmount(request.getClaimAmount());

        // 创建并发送命令
        UpdateClaimCommand command = new UpdateClaimCommand(
                ClaimId.of(claimId),
                ClaimEnum.ClaimType.fromCode(request.getClaimType()),
                request.getIncidentDate(),
                request.getIncidentDescription(),
                ClaimAmount.of(request.getClaimAmount()));
        commandGateway.sendAndWait(command);
    }

    @Transactional
    public void changeClaimStatus(String claimId, ChangeClaimStatusRequest request) {
        // 验证理赔状态
        claimService.validateClaimStatus(request.getNewStatus());

        // 创建并发送命令（状态字符串按 code 解析为枚举）
        ChangeClaimStatusCommand command = new ChangeClaimStatusCommand(
                ClaimId.of(claimId),
                ClaimStatus.fromCode(request.getNewStatus()),
                request.getReason());
        commandGateway.sendAndWait(command);
    }

    @Transactional
    public void updateClaimStatus(String claimId, String status) {
        changeClaimStatus(claimId, new ChangeClaimStatusRequest(status, "状态更新"));
    }

    /**
     * 提交查勘：装配查勘值对象并发命令，推进理赔阶段至 SURVEY。
     */
    @Transactional
    public void submitSurvey(String claimId, SubmitSurveyRequest request) {
        Survey survey = new Survey(request.getSurveyorId(), request.getSurveyReport(), request.getPhotos(),
                request.getConclusion(), LocalDateTime.now());
        commandGateway.sendAndWait(new SubmitSurveyCommand(ClaimId.of(claimId), survey));
    }

    /**
     * 提交定损：装配定损值对象（含明细项）并发命令，推进理赔阶段至 LOSS_ASSESS。
     */
    @Transactional
    public void submitLossAssessment(String claimId, SubmitLossAssessmentRequest request) {
        List<LossAssessment.LossItem> items = request.getItems() == null ? List.of()
                : request.getItems().stream()
                        .map(i -> new LossAssessment.LossItem(i.getItemName(), i.getAmount()))
                        .collect(Collectors.toList());
        LossAssessment lossAssessment = new LossAssessment(request.getAssessedAmount(), items,
                request.getSalvageValue(), request.getLiabilityRatio(), request.getAssessorId());
        commandGateway.sendAndWait(new SubmitLossAssessmentCommand(ClaimId.of(claimId), lossAssessment));
    }

    /**
     * 核赔结算：提交核赔结论，理赔流转至 APPROVED 并进入赔付中（给付方式 code 转枚举）。
     */
    @Transactional
    public void settleClaim(String claimId, SettleClaimRequest request) {
        SettleClaimCommand command = new SettleClaimCommand(ClaimId.of(claimId), request.getSettledAmount(),
                ClaimEnum.PayoutMethod.fromCode(request.getPayoutMethod()), request.getPayeeAccount(),
                request.getConclusion());
        commandGateway.sendAndWait(command);
    }

    /**
     * 身故给付结算（寿险身故理赔专属）：委托理算编排器。
     * <p>
     * {@link ClaimSettlementOrchestrator} 取保单基本保额精算给付总额（CLAIM-2，禁止透传金额），
     * 按受益人比例分配（比例之和与份额之和双重守恒由 {@code BenefitCalculation} 值对象守护），
     * 装配身故证据并派发 {@code SettleDeathBenefitCommand}。给付后由 claim 域发布
     * {@code DeathBenefitSettledEvent}，policy 域防腐监听器据此终止保单（给付后保单责任终结）。
     * </p>
     */
    @Transactional
    public void settleDeathBenefit(String claimId, SettleDeathBenefitRequest request) {
        claimSettlementOrchestrator.settleDeathBenefit(claimId, request);
    }

    /**
     * 全残给付结算（寿险/意外险全残理赔专属，CLAIM-6）：委托理算编排器。
     * <p>
     * {@link ClaimSettlementOrchestrator} 取保单基本保额/账户价值精算给付总额
     * （max(账户价值, 基本保额)，禁止透传金额），按受益人比例分配（双重守恒由
     * {@code BenefitCalculation} 值对象守护），装配全残证据并派发 {@code SettleDisabilityBenefitCommand}。
     * 给付后由 claim 域发布 {@code DisabilityBenefitSettledEvent}，policy 域防腐监听器据此终止保单（同身故）。
     * </p>
     */
    @Transactional
    public void settleDisabilityBenefit(String claimId, SettleDisabilityBenefitRequest request) {
        claimSettlementOrchestrator.settleDisabilityBenefit(claimId, request);
    }

    /**
     * 拒赔（核赔否决）：拒赔原因 code 还原枚举后直发 {@link RejectClaimCommand}（单聚合单命令，KISS）。
     * 仅 PENDING/PROCESSING 可拒赔，状态流转合法性由聚合根守护，通过后发布 {@code ClaimRejectedEvent}。
     */
    @Transactional
    public void rejectClaim(String claimId, RejectClaimRequest request) {
        RejectClaimCommand command = new RejectClaimCommand(ClaimId.of(claimId),
                RejectReason.fromCode(request.getReasonCode()), request.getComment());
        commandGateway.sendAndWait(command);
    }

    /**
     * 结案（归档）：直发 {@link CloseClaimCommand}，仅 PAID/REJECTED 终态可结案（聚合根守护），
     * 通过后发布 {@code ClaimClosedEvent}。
     */
    @Transactional
    public void closeClaim(String claimId) {
        commandGateway.sendAndWait(new CloseClaimCommand(ClaimId.of(claimId)));
    }

    /**
     * 打标警示标记（手动入口：人工复核标记/规则引擎风险评分回写）：类型 code 还原枚举后直发
     * {@link FlagClaimAlertCommand}（单聚合单命令，KISS），聚合根按类型合并去重（幂等），
     * 通过后投影至读模型 {@code alert_flags} 列供快赔通道判据使用。
     */
    @Transactional
    public void flagAlert(String claimId, FlagClaimAlertRequest request) {
        List<AlertFlag> flags = request.getFlags().stream()
                .map(item -> new AlertFlag(AlertType.fromCode(item.getTypeCode()), item.getRuleCode()))
                .toList();
        commandGateway.sendAndWait(new FlagClaimAlertCommand(ClaimId.of(claimId), flags));
    }

    /**
     * 快赔自动核赔（小额快赔通道，产品文档 §2.10）：委托 {@link QuickPayOrchestrator} 判据编排
     * （规则匹配 + 金额/状态/欺诈警示判据），判据全过自动核赔（APPROVED → 结算）并打快赔统计标记。
     */
    @Transactional
    public void quickPay(String claimId) {
        quickPayOrchestrator.executeQuickPay(claimId, tenantContext.getCurrentTenantId());
    }
}
