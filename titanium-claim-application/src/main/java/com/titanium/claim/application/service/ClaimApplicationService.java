package com.titanium.claim.application.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.claim.application.dto.ChangeClaimStatusRequestDTO;
import com.titanium.claim.application.dto.ClaimResponseDTO;
import com.titanium.claim.application.dto.CreateClaimRequestDTO;
import com.titanium.claim.application.dto.SettleClaimRequestDTO;
import com.titanium.claim.application.dto.SettleDeathBenefitRequestDTO;
import com.titanium.claim.application.dto.SubmitLossAssessmentRequestDTO;
import com.titanium.claim.application.dto.SubmitSurveyRequestDTO;
import com.titanium.claim.application.dto.UpdateClaimRequestDTO;
import com.titanium.claim.command.ChangeClaimStatusCommand;
import com.titanium.claim.command.CreateClaimCommand;
import com.titanium.claim.command.SettleClaimCommand;
import com.titanium.claim.command.SettleDeathBenefitCommand;
import com.titanium.claim.command.SubmitLossAssessmentCommand;
import com.titanium.claim.command.SubmitSurveyCommand;
import com.titanium.claim.command.UpdateClaimCommand;
import com.titanium.claim.common.enums.ClaimStatus;
import com.titanium.claim.common.exception.PolicyNotActiveException;
import com.titanium.claim.query.result.ClaimQueryResult;
import com.titanium.claim.query.service.ClaimQueryService;
import com.titanium.claim.service.ClaimService;
import com.titanium.claim.valueobject.BenefitCalculation;
import com.titanium.claim.valueobject.ClaimAmount;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.CustomerId;
import com.titanium.claim.valueobject.DeathClaimEvidence;
import com.titanium.claim.valueobject.LossAssessment;
import com.titanium.claim.valueobject.PolicyId;
import com.titanium.claim.valueobject.Survey;
import com.titanium.metadata.enums.claim.ClaimEnum;
import com.titanium.policy.api.dto.PolicyDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 理赔应用服务（写侧入口门面 + 读侧编排）
 * <p>
 * 写用例：校验 → 构造命令 → {@code CommandGateway} 发送；读用例：委托 CQRS 读侧 {@link ClaimQueryService}
 * 查询读模型（{@code t_claim_view}），组装为对外 {@link ClaimResponseDTO}。
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClaimApplicationService {

    private final CommandGateway    commandGateway;
    private final ClaimService      claimService;
    private final PolicyService     policyService;
    private final ClaimQueryService claimQueryService;

    @Transactional
    public String createClaim(CreateClaimRequestDTO request) {
        // 1. 验证理赔金额
        claimService.validateClaimAmount(request.getClaimAmount());

        // 2. 验证保单是否存在且有效
        validatePolicy(request.getPolicyId());

        // 3. 生成理赔ID和理赔编号
        ClaimId claimId = ClaimId.generate();
        String claimNumber = claimService.generateClaimNumber();

        // 4. 创建并发送命令
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

        return claimId.value();
    }

    /**
     * 验证保单存在且状态为 ACTIVE。
     * <p>
     * 说明：多租户上下文贯穿待事件总线/网关就绪后补齐，此处沿用默认租户；校验失败抛领域异常
     * {@link PolicyNotActiveException}（替代原裸 {@code RuntimeException}）。
     * </p>
     */
    private void validatePolicy(String policyId) {
        PolicyDTO policy = policyService.getPolicy(policyId, "default-tenant");
        String statusCode = policy == null || policy.getStatus() == null ? null : policy.getStatus().getCode();
        if (!"ACTIVE".equals(statusCode)) {
            log.error("保单验证失败, policyId={}, status={}", policyId, statusCode);
            throw new PolicyNotActiveException();
        }
        log.info("保单验证通过, policyId={}, status={}", policyId, statusCode);
    }

    @Transactional
    public void updateClaim(String claimId, UpdateClaimRequestDTO request) {
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
    public void changeClaimStatus(String claimId, ChangeClaimStatusRequestDTO request) {
        // 验证理赔状态
        claimService.validateClaimStatus(request.getNewStatus());

        // 创建并发送命令（DTO 状态字符串按 code 解析为枚举）
        ChangeClaimStatusCommand command = new ChangeClaimStatusCommand(
                ClaimId.of(claimId),
                ClaimStatus.fromCode(request.getNewStatus()),
                request.getReason());
        commandGateway.sendAndWait(command);
    }

    @Transactional
    public void updateClaimStatus(String claimId, String status) {
        changeClaimStatus(claimId, new ChangeClaimStatusRequestDTO(status, "状态更新"));
    }

    /**
     * 提交查勘：装配查勘值对象并发命令，推进理赔阶段至 SURVEY。
     */
    @Transactional
    public void submitSurvey(String claimId, SubmitSurveyRequestDTO request) {
        Survey survey = new Survey(request.getSurveyorId(), request.getSurveyReport(), request.getPhotos(),
                request.getConclusion(), LocalDateTime.now());
        commandGateway.sendAndWait(new SubmitSurveyCommand(ClaimId.of(claimId), survey));
    }

    /**
     * 提交定损：装配定损值对象（含明细项）并发命令，推进理赔阶段至 LOSS_ASSESS。
     */
    @Transactional
    public void submitLossAssessment(String claimId, SubmitLossAssessmentRequestDTO request) {
        List<LossAssessment.LossItem> items = request.getItems() == null ? List.of()
                : request.getItems().stream()
                        .map(i -> new LossAssessment.LossItem(i.getItemName(), i.getAmount()))
                        .collect(Collectors.toList());
        LossAssessment lossAssessment = new LossAssessment(request.getAssessedAmount(), items,
                request.getLiabilityRatio(), request.getAssessorId());
        commandGateway.sendAndWait(new SubmitLossAssessmentCommand(ClaimId.of(claimId), lossAssessment));
    }

    /**
     * 核赔结算：提交核赔结论，理赔流转至 PAID（给付方式 code 转枚举）。
     */
    @Transactional
    public void settleClaim(String claimId, SettleClaimRequestDTO request) {
        SettleClaimCommand command = new SettleClaimCommand(ClaimId.of(claimId), request.getSettledAmount(),
                ClaimEnum.PayoutMethod.fromCode(request.getPayoutMethod()), request.getPayeeAccount(),
                request.getConclusion());
        commandGateway.sendAndWait(command);
    }

    /**
     * 身故给付结算（寿险身故理赔专属，APPROVED → PAID）。
     * <p>
     * 组装身故证据与受益人份额核算，派发 {@link SettleDeathBenefitCommand}。给付后由 claim 域发布
     * {@code DeathBenefitSettledEvent}，policy 域防腐监听器据此终止保单（给付后保单责任终结）。
     * 受益人份额之和须等于给付总额的不变量由 {@code BenefitCalculation} 值对象守护。
     * </p>
     */
    @Transactional
    public void settleDeathBenefit(String claimId, SettleDeathBenefitRequestDTO request) {
        DeathClaimEvidence evidence = new DeathClaimEvidence(request.getDeathCertificateNo(), request.getDeathDate(),
                request.getDeathCause(), request.isHouseholdCancelled(), request.getBeneficiaryProofNo(),
                LocalDateTime.now());
        List<BenefitCalculation.BeneficiaryShare> shares = request.getShares() == null ? List.of()
                : request.getShares().stream()
                        .map(s -> new BenefitCalculation.BeneficiaryShare(s.getBeneficiaryId(), s.getBeneficiaryName(),
                                s.getBenefitRatio(), s.getAmount()))
                        .collect(Collectors.toList());
        BenefitCalculation benefitCalculation = new BenefitCalculation(request.getTotalBenefit(), shares);
        SettleDeathBenefitCommand command = new SettleDeathBenefitCommand(ClaimId.of(claimId), evidence,
                benefitCalculation, ClaimEnum.PayoutMethod.fromCode(request.getPayoutMethod()), request.getConclusion());
        commandGateway.sendAndWait(command);
    }

    // ==================== 读侧：委托 CQRS 查询服务 ====================

    @Transactional(readOnly = true)
    public Optional<ClaimResponseDTO> getClaim(String claimId) {
        return claimQueryService.getClaimSummary(claimId).map(this::toResponseDTO);
    }

    @Transactional(readOnly = true)
    public List<ClaimResponseDTO> getClaimsByCustomerId(String customerId) {
        return claimQueryService.getClaimSummariesByCustomerId(customerId)
                .stream().map(this::toResponseDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ClaimResponseDTO> getClaimsByPolicyId(String policyId) {
        return claimQueryService.getClaimSummariesByPolicyId(policyId)
                .stream().map(this::toResponseDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ClaimResponseDTO> getClaimsByStatus(String status) {
        return claimQueryService.getClaimSummariesByStatus(status)
                .stream().map(this::toResponseDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ClaimResponseDTO> getAllClaims() {
        return claimQueryService.getAllClaimSummaries()
                .stream().map(this::toResponseDTO).collect(Collectors.toList());
    }

    // 读模型查询结果 → 对外响应 DTO
    private ClaimResponseDTO toResponseDTO(ClaimQueryResult result) {
        ClaimResponseDTO response = new ClaimResponseDTO();
        response.setClaimId(result.getClaimId());
        response.setCustomerId(result.getCustomerId());
        response.setPolicyId(result.getPolicyId());
        response.setClaimNumber(result.getClaimNumber());
        response.setClaimType(result.getClaimType());
        response.setIncidentDate(result.getIncidentDate());
        response.setIncidentDescription(result.getIncidentDescription());
        response.setClaimAmount(result.getClaimAmount());
        response.setStatus(result.getStatus());
        response.setCreatedAt(result.getCreatedAt());
        response.setUpdatedAt(result.getUpdatedAt());
        return response;
    }
}
