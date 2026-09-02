package com.titanium.claim.web.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.claim.api.response.ClaimStatisticsResponse;
import com.titanium.claim.application.command.ClaimCommandService;
import com.titanium.claim.application.query.ClaimAppQueryService;
import com.titanium.claim.application.query.ReimbursementAdjustmentQueryService;
import com.titanium.claim.query.query.SearchClaimSummariesQuery;
import com.titanium.claim.web.dto.CreateClaimDTO;
import com.titanium.claim.web.dto.FlagClaimAlertDTO;
import com.titanium.claim.web.dto.RejectClaimDTO;
import com.titanium.claim.web.dto.SettleClaimDTO;
import com.titanium.claim.web.dto.SettleDeathBenefitDTO;
import com.titanium.claim.web.dto.SettleDisabilityBenefitDTO;
import com.titanium.claim.web.dto.SubmitLossAssessmentDTO;
import com.titanium.claim.web.dto.SubmitSurveyDTO;
import com.titanium.claim.web.dto.UpdateClaimDTO;
import com.titanium.claim.web.dto.assessment.ReimbursementAdjustmentDTO;
import com.titanium.claim.web.mapper.ClaimStatisticsWebMapper;
import com.titanium.claim.web.mapper.ClaimWebMapper;
import com.titanium.claim.web.response.ClaimResponseVO;
import com.titanium.claim.web.response.assessment.ReimbursementAdjustmentVO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 理赔控制器（后台/端上 HTTP 入口）
 * <p>
 * 面向管理后台/端上，路径 {@code /web/v1/claims}，入参为 web 层 {@code XxxDTO}（web/dto）、出参 {@code ClaimResponseVO}，
 * <b>不再 implements ClaimApi</b>（远程契约由 {@code ClaimApiProvider} 承接）。经 {@link ClaimWebMapper} 把
 * Request VO 翻译为应用层入参 DTO，交 {@link ClaimCommandService} 与 {@link ClaimAppQueryService} 编排；读侧查询结果转 VO 返回。
 * 与 {@code ClaimApiProvider} 平行收敛到同一应用层门面，Controller 零业务逻辑。
 * </p>
 */
@RestController
@RequestMapping("/web/v1/claims")
@RequiredArgsConstructor
public class ClaimController {

    private final ClaimCommandService  claimCommandService;
    private final ClaimAppQueryService claimAppQueryService;
    private final ClaimWebMapper          claimWebMapper;
    private final ClaimStatisticsWebMapper claimStatisticsWebMapper;
    private final ReimbursementAdjustmentQueryService reimbursementAdjustmentQueryService;

    /**
     * 理赔统计（管理后台看板聚合）
     * <p>
     * 返回待处理理赔数、今日报案数、理赔总数及累计已结案赔付金额。强制按 {@code X-Tenant-Id} 租户隔离。
     * </p>
     *
     * @param tenantId 租户ID
     * @return 理赔统计结果
     */
    @GetMapping("/statistics")
    public ResponseEntity<ClaimStatisticsResponse> getStatistics(
            @RequestHeader("X-Tenant-Id") String tenantId) {
        return ResponseEntity.ok(
                claimStatisticsWebMapper.toResponse(claimAppQueryService.getStatistics(tenantId)));
    }

    /**
     * 创建理赔案件
     */
    @PostMapping
    public ResponseEntity<String> createClaim(@RequestBody @Valid CreateClaimDTO requestVO) {
        String claimId = claimCommandService.createClaim(claimWebMapper.toCreateRequest(requestVO));
        return new ResponseEntity<>(claimId, HttpStatus.CREATED);
    }

    /**
     * 更新理赔案件
     */
    @PutMapping("/{claimId}")
    public ResponseEntity<Void> updateClaim(@PathVariable("claimId") String claimId,
                                            @RequestBody @Valid UpdateClaimDTO requestVO) {
        claimCommandService.updateClaim(claimId, claimWebMapper.toUpdateRequest(requestVO));
        return ResponseEntity.noContent().build();
    }

    /**
     * 更新理赔状态
     */
    @PutMapping("/{claimId}/status")
    public ResponseEntity<Void> updateClaimStatus(@PathVariable("claimId") String claimId,
                                                  @RequestParam("status") String status) {
        claimCommandService.updateClaimStatus(claimId, status);
        return ResponseEntity.noContent().build();
    }

    /**
     * 获取理赔案件详情
     */
    @GetMapping("/{claimId}")
    public ResponseEntity<ClaimResponseVO> getClaim(
            @PathVariable("claimId") String claimId,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        return claimAppQueryService.getClaim(claimId, tenantId)
                .map(claimWebMapper::toVO)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 根据客户ID查询理赔案件列表
     */
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<ClaimResponseVO>> getClaimsByCustomerId(
            @PathVariable("customerId") String customerId,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        return ResponseEntity.ok(claimAppQueryService.getClaimsByCustomerId(customerId, tenantId)
                .stream().map(claimWebMapper::toVO).toList());
    }

    /**
     * 根据保单ID查询理赔案件列表
     */
    @GetMapping("/policy/{policyId}")
    public ResponseEntity<List<ClaimResponseVO>> getClaimsByPolicyId(
            @PathVariable("policyId") String policyId,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        return ResponseEntity.ok(claimAppQueryService.getClaimsByPolicyId(policyId, tenantId)
                .stream().map(claimWebMapper::toVO).toList());
    }

    /**
     * 根据状态查询理赔案件列表
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<ClaimResponseVO>> getClaimsByStatus(
            @PathVariable("status") String status,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        return ResponseEntity.ok(claimAppQueryService.getClaimsByStatus(status, tenantId)
                .stream().map(claimWebMapper::toVO).toList());
    }

    /**
     * 查询所有理赔案件列表
     */
    @GetMapping
    public ResponseEntity<List<ClaimResponseVO>> getAllClaims(
            @RequestHeader("X-Tenant-Id") String tenantId) {
        return ResponseEntity.ok(claimAppQueryService.getAllClaims(tenantId)
                .stream().map(claimWebMapper::toVO).toList());
    }

    /**
     * 提交查勘（推进理赔阶段至 SURVEY）
     */
    @PostMapping("/{claimId}/survey")
    public ResponseEntity<Void> submitSurvey(@PathVariable("claimId") String claimId,
                                             @RequestBody @Valid SubmitSurveyDTO requestVO) {
        claimCommandService.submitSurvey(claimId, claimWebMapper.toSurveyRequest(requestVO));
        return ResponseEntity.noContent().build();
    }

    /**
     * 提交定损（推进理赔阶段至 LOSS_ASSESS）
     */
    @PostMapping("/{claimId}/loss-assessment")
    public ResponseEntity<Void> submitLossAssessment(@PathVariable("claimId") String claimId,
                                                     @RequestBody @Valid SubmitLossAssessmentDTO requestVO) {
        claimCommandService.submitLossAssessment(claimId, claimWebMapper.toLossAssessmentRequest(requestVO));
        return ResponseEntity.noContent().build();
    }

    /**
     * 核赔结算（APPROVED → PAID）
     */
    @PostMapping("/{claimId}/settlement")
    public ResponseEntity<Void> settleClaim(@PathVariable("claimId") String claimId,
                                            @RequestBody @Valid SettleClaimDTO requestVO) {
        claimCommandService.settleClaim(claimId, claimWebMapper.toSettleRequest(requestVO));
        return ResponseEntity.noContent().build();
    }

    /**
     * 身故给付结算（寿险专属，APPROVED → PAID，按受益人份额一次性给付）
     */
    @PostMapping("/{claimId}/death-benefit")
    public ResponseEntity<Void> settleDeathBenefit(@PathVariable("claimId") String claimId,
                                                   @RequestBody @Valid SettleDeathBenefitDTO requestVO) {
        claimCommandService.settleDeathBenefit(claimId, claimWebMapper.toDeathBenefitRequest(requestVO));
        return ResponseEntity.noContent().build();
    }

    /**
     * 全残给付结算（寿险/意外险专属，CLAIM-6，APPROVED → PAID，按受益人份额一次性给付）
     */
    @PostMapping("/{claimId}/disability-benefit")
    public ResponseEntity<Void> settleDisabilityBenefit(@PathVariable("claimId") String claimId,
                                                        @RequestBody @Valid SettleDisabilityBenefitDTO requestVO) {
        claimCommandService.settleDisabilityBenefit(claimId, claimWebMapper.toDisabilityBenefitRequest(requestVO));
        return ResponseEntity.noContent().build();
    }

    /**
     * 打标警示标记（手动：人工复核/规则引擎回写，聚合根按类型合并去重幂等）
     */
    @PostMapping("/{claimId}/alert-flags")
    public ResponseEntity<Void> flagClaimAlert(@PathVariable("claimId") String claimId,
                                               @RequestBody @Valid FlagClaimAlertDTO requestVO) {
        claimCommandService.flagAlert(claimId, claimWebMapper.toFlagAlertRequest(requestVO));
        return ResponseEntity.noContent().build();
    }

    /**
     * 快赔自动核赔（小额快赔通道：规则匹配 + 判据全过自动核赔并打快赔统计标记）
     */
    @PostMapping("/{claimId}/quick-pay")
    public ResponseEntity<Void> quickPay(@PathVariable("claimId") String claimId) {
        claimCommandService.quickPay(claimId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 拒赔（核赔否决，PENDING/PROCESSING → REJECTED）
     */
    @PostMapping("/{claimId}/reject")
    public ResponseEntity<Void> rejectClaim(@PathVariable("claimId") String claimId,
                                            @RequestBody @Valid RejectClaimDTO requestVO) {
        claimCommandService.rejectClaim(claimId, claimWebMapper.toRejectRequest(requestVO));
        return ResponseEntity.noContent().build();
    }

    /**
     * 结案（归档，PAID/REJECTED → CLOSED）
     */
    @PostMapping("/{claimId}/close")
    public ResponseEntity<Void> closeClaim(@PathVariable("claimId") String claimId) {
        claimCommandService.closeClaim(claimId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 多条件搜索理赔案件（数据库侧过滤 + 分页）
     * <p>
     * 支持按理赔编号、保单ID、客户ID、状态任意组合过滤，参数均可选；过滤与分页已下沉 CQRS 读侧
     * （{@code ClaimQueryService.searchClaimSummaries} 的 JPA Specification 动态组装），
     * Controller 只组装查询条件记录并委托应用层读入口，零业务逻辑。
     * </p>
     */
    @GetMapping("/search")
    public ResponseEntity<List<ClaimResponseVO>> searchClaims(
            @RequestParam(required = false) String claimNo,
            @RequestParam(required = false) String policyId,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        List<ClaimResponseVO> result = claimAppQueryService
                .searchClaims(new SearchClaimSummariesQuery(claimNo, policyId, customerId, status), page, size, tenantId)
                .stream().map(claimWebMapper::toVO).toList();
        return ResponseEntity.ok(result);
    }

    /**
     * 报销理算（健康险/宠物险，核赔理算试算）
     * <p>
     * 系统按赔付规则配置（免赔额/比例/限额）与医院网络台账裁决定点/非定点结算渠道并计算应付金额，
     * 金额由领域服务精算、不信任前端透传。定点医院套用台账比例、非定点套用规则非定点档位
     * （缺省回落基础比例半数）。
     * </p>
     */
    @PostMapping("/reimbursement-adjustment")
    public ResponseEntity<ReimbursementAdjustmentVO> adjustReimbursement(
            @RequestBody @Valid ReimbursementAdjustmentDTO dto) {
        return ResponseEntity.ok(claimWebMapper.toReimbursementVO(
                reimbursementAdjustmentQueryService.adjust(claimWebMapper.toReimbursementRequest(dto))));
    }
}
