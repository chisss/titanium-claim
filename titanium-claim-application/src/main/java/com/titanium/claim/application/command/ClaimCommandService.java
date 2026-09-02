package com.titanium.claim.application.command;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.claim.application.model.assessment.SubmitLossAssessmentRequest;
import com.titanium.claim.application.model.assessment.SubmitSurveyRequest;
import com.titanium.claim.application.model.issuance.CreateClaimRequest;
import com.titanium.claim.application.model.maintenance.ChangeClaimStatusRequest;
import com.titanium.claim.application.model.maintenance.UpdateClaimRequest;
import com.titanium.claim.application.model.settlement.SettleClaimRequest;
import com.titanium.claim.application.model.settlement.SettleDeathBenefitRequest;
import com.titanium.claim.application.orchestration.assessment.ClaimSettlementOrchestrator;
import com.titanium.claim.application.orchestration.issuance.ClaimRegistrationOrchestrator;
import com.titanium.claim.command.ChangeClaimStatusCommand;
import com.titanium.claim.command.SettleClaimCommand;
import com.titanium.claim.command.SubmitLossAssessmentCommand;
import com.titanium.claim.command.SubmitSurveyCommand;
import com.titanium.claim.command.UpdateClaimCommand;
import com.titanium.claim.common.enums.ClaimStatus;
import com.titanium.claim.service.ClaimService;
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

    /**
     * 创建理赔案件（报案）：委托报案校验链编排器，返回理赔 ID。
     */
    @Transactional
    public String createClaim(CreateClaimRequest request) {
        return claimRegistrationOrchestrator.registerClaim(request);
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
                request.getLiabilityRatio(), request.getAssessorId());
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
}
