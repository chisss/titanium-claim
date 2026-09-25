package com.titanium.claim.web.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.claim.api.response.ClaimStatisticsResponse;
import com.titanium.claim.application.command.ClaimCommandService;
import com.titanium.claim.application.query.ClaimAppQueryService;
import com.titanium.claim.application.query.ReimbursementAdjustmentQueryService;
import com.titanium.claim.query.query.SearchClaimSummariesQuery;
import com.titanium.claim.web.dto.assessment.ReimbursementAdjustmentDTO;
import com.titanium.claim.web.dto.assessment.SubmitLossAssessmentDTO;
import com.titanium.claim.web.dto.assessment.SubmitSurveyDTO;
import com.titanium.claim.web.dto.issuance.CreateClaimDTO;
import com.titanium.claim.web.dto.maintenance.FlagClaimAlertDTO;
import com.titanium.claim.web.dto.maintenance.RejectClaimDTO;
import com.titanium.claim.web.dto.maintenance.UpdateClaimDTO;
import com.titanium.claim.web.dto.settlement.SettleClaimDTO;
import com.titanium.claim.web.dto.settlement.SettleDeathBenefitDTO;
import com.titanium.claim.web.dto.settlement.SettleDisabilityBenefitDTO;
import com.titanium.claim.web.dto.settlement.SettleReimbursementDTO;
import com.titanium.claim.web.mapper.ClaimStatisticsWebMapper;
import com.titanium.claim.web.mapper.ClaimWebMapper;
import com.titanium.claim.web.response.ClaimResponseVO;
import com.titanium.claim.web.response.assessment.ReimbursementAdjustmentVO;
import com.titanium.common.context.RequestContextHolder;
import com.titanium.metadata.errorcode.ClaimErrorCode;
import com.titanium.metadata.response.ApiResponse;

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
     * @return 理赔统计结果
     */
    @GetMapping("/statistics")
    public ApiResponse<ClaimStatisticsResponse> getStatistics(
         ) {
        String tenantId = RequestContextHolder.requireTenantId();
        return ApiResponse.success(
                claimStatisticsWebMapper.toResponse(claimAppQueryService.getStatistics(tenantId)));
    }

    /**
     * 创建理赔案件
     */
    @PostMapping
    public ResponseEntity<ApiResponse<String>> createClaim(@RequestBody @Valid CreateClaimDTO requestVO) {
        String claimId = claimCommandService.createClaim(claimWebMapper.toCreateRequest(requestVO));
        // 保留 201：创建语义属 HTTP 传输层，不并入 ApiResponse.code
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(claimId));
    }

    /**
     * 更新理赔案件
     */
    @PutMapping("/{claimId}")
    public ApiResponse<Void> updateClaim(@PathVariable String claimId,
                                         @RequestBody @Valid UpdateClaimDTO requestVO) {
        claimCommandService.updateClaim(claimId, claimWebMapper.toUpdateRequest(requestVO));
        return ApiResponse.success();
    }

    /**
     * 更新理赔状态
     */
    @PutMapping("/{claimId}/status")
    public ApiResponse<Void> updateClaimStatus(@PathVariable String claimId,
                                               @RequestParam String status) {
        claimCommandService.updateClaimStatus(claimId, status);
        return ApiResponse.success();
    }

    /**
     * 获取理赔案件详情
     */
    @GetMapping("/{claimId}")
    public ResponseEntity<ApiResponse<ClaimResponseVO>> getClaim(
            @PathVariable String claimId) {
        String tenantId = RequestContextHolder.requireTenantId();
        // R8-05 同族：未命中**保留 404**（不放宽成 200/403，避免成为探测 id 是否存在的侧信道），
        // 但 body 必须带信封 —— BFF 对下游 404 的 message 一律取自响应体，空 body 会让调用方无从诊断
        return claimAppQueryService.getClaim(claimId, tenantId)
                .map(claimWebMapper::toVO)
                .map(vo -> ResponseEntity.ok(ApiResponse.success(vo)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(ClaimErrorCode.CLAIM_NOT_EXIST,
                                "理赔案件不存在: " + claimId)));
    }

    /**
     * 根据客户ID查询理赔案件列表
     */
    @GetMapping("/customer/{customerId}")
    public ApiResponse<List<ClaimResponseVO>> getClaimsByCustomerId(
         @PathVariable String customerId) {
        String tenantId = RequestContextHolder.requireTenantId();
        return ApiResponse.success(claimAppQueryService.getClaimsByCustomerId(customerId, tenantId)
                .stream().map(claimWebMapper::toVO).toList());
    }

    /**
     * 根据保单ID查询理赔案件列表
     */
    @GetMapping("/policy/{policyId}")
    public ApiResponse<List<ClaimResponseVO>> getClaimsByPolicyId(
         @PathVariable String policyId) {
        String tenantId = RequestContextHolder.requireTenantId();
        return ApiResponse.success(claimAppQueryService.getClaimsByPolicyId(policyId, tenantId)
                .stream().map(claimWebMapper::toVO).toList());
    }

    /**
     * 根据状态查询理赔案件列表
     */
    @GetMapping("/status/{status}")
    public ApiResponse<List<ClaimResponseVO>> getClaimsByStatus(
         @PathVariable String status) {
        String tenantId = RequestContextHolder.requireTenantId();
        return ApiResponse.success(claimAppQueryService.getClaimsByStatus(status, tenantId)
                .stream().map(claimWebMapper::toVO).toList());
    }

    /**
     * 查询所有理赔案件列表
     */
    @GetMapping
    public ApiResponse<List<ClaimResponseVO>> getAllClaims(
         ) {
        String tenantId = RequestContextHolder.requireTenantId();
        return ApiResponse.success(claimAppQueryService.getAllClaims(tenantId)
                .stream().map(claimWebMapper::toVO).toList());
    }

    /**
     * 提交查勘（推进理赔阶段至 SURVEY）
     */
    @PostMapping("/{claimId}/survey")
    public ApiResponse<Void> submitSurvey(@PathVariable String claimId,
                                          @RequestBody @Valid SubmitSurveyDTO requestVO) {
        claimCommandService.submitSurvey(claimId, claimWebMapper.toSurveyRequest(requestVO));
        return ApiResponse.success();
    }

    /**
     * 提交定损（推进理赔阶段至 LOSS_ASSESS）
     */
    @PostMapping("/{claimId}/loss-assessment")
    public ApiResponse<Void> submitLossAssessment(@PathVariable String claimId,
                                                  @RequestBody @Valid SubmitLossAssessmentDTO requestVO) {
        claimCommandService.submitLossAssessment(claimId, claimWebMapper.toLossAssessmentRequest(requestVO));
        return ApiResponse.success();
    }

    /**
     * 核赔结算（APPROVED → PAID）
     */
    @PostMapping("/{claimId}/settlement")
    public ApiResponse<Void> settleClaim(@PathVariable String claimId,
                                         @RequestBody @Valid SettleClaimDTO requestVO) {
        claimCommandService.settleClaim(claimId, claimWebMapper.toSettleRequest(requestVO));
        return ApiResponse.success();
    }

    /**
     * 身故给付结算（寿险专属，APPROVED → PAID，按受益人份额一次性给付）
     */
    @PostMapping("/{claimId}/death-benefit")
    public ApiResponse<Void> settleDeathBenefit(@PathVariable String claimId,
                                                @RequestBody @Valid SettleDeathBenefitDTO requestVO) {
        claimCommandService.settleDeathBenefit(claimId, claimWebMapper.toDeathBenefitRequest(requestVO));
        return ApiResponse.success();
    }

    /**
     * 全残给付结算（寿险/意外险专属，CLAIM-6，APPROVED → PAID，按受益人份额一次性给付）
     */
    @PostMapping("/{claimId}/disability-benefit")
    public ApiResponse<Void> settleDisabilityBenefit(@PathVariable String claimId,
                                                     @RequestBody @Valid SettleDisabilityBenefitDTO requestVO) {
        claimCommandService.settleDisabilityBenefit(claimId, claimWebMapper.toDisabilityBenefitRequest(requestVO));
        return ApiResponse.success();
    }

    /**
     * 打标警示标记（手动：人工复核/规则引擎回写，聚合根按类型合并去重幂等）
     */
    @PostMapping("/{claimId}/alert-flags")
    public ApiResponse<Void> flagClaimAlert(@PathVariable String claimId,
                                            @RequestBody @Valid FlagClaimAlertDTO requestVO) {
        claimCommandService.flagAlert(claimId, claimWebMapper.toFlagAlertRequest(requestVO));
        return ApiResponse.success();
    }

    /**
     * 快赔自动核赔（小额快赔通道：规则匹配 + 判据全过自动核赔并打快赔统计标记）
     */
    @PostMapping("/{claimId}/quick-pay")
    public ApiResponse<Void> quickPay(@PathVariable String claimId) {
        claimCommandService.quickPay(claimId);
        return ApiResponse.success();
    }

    /**
     * 拒赔（核赔否决，PENDING/PROCESSING → REJECTED）
     */
    @PostMapping("/{claimId}/reject")
    public ApiResponse<Void> rejectClaim(@PathVariable String claimId,
                                         @RequestBody @Valid RejectClaimDTO requestVO) {
        claimCommandService.rejectClaim(claimId, claimWebMapper.toRejectRequest(requestVO));
        return ApiResponse.success();
    }

    /**
     * 结案（归档，PAID/REJECTED → CLOSED）
     */
    @PostMapping("/{claimId}/close")
    public ApiResponse<Void> closeClaim(@PathVariable String claimId) {
        claimCommandService.closeClaim(claimId);
        return ApiResponse.success();
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
    public ApiResponse<List<ClaimResponseVO>> searchClaims(
         @RequestParam(required = false) String claimNo,
         @RequestParam(required = false) String policyId,
         @RequestParam(required = false) String customerId,
         @RequestParam(required = false) String status,
         @RequestParam(defaultValue = "0") int page,
         @RequestParam(defaultValue = "10") int size) {
        String tenantId = RequestContextHolder.requireTenantId();
        List<ClaimResponseVO> result = claimAppQueryService
                .searchClaims(new SearchClaimSummariesQuery(claimNo, policyId, customerId, status), page, size, tenantId)
                .stream().map(claimWebMapper::toVO).toList();
        return ApiResponse.success(result);
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
    public ApiResponse<ReimbursementAdjustmentVO> adjustReimbursement(
         @RequestBody @Valid ReimbursementAdjustmentDTO dto) {
        return ApiResponse.success(claimWebMapper.toReimbursementVO(
                reimbursementAdjustmentQueryService.adjust(claimWebMapper.toReimbursementRequest(dto))));
    }

    /**
     * 报销理算结算（健康险/宠物险，案件须已核赔通过 APPROVED）
     * <p>
     * 对已核赔通过的报销类案件<b>按理算金额结算</b>：给付金额由系统按赔付规则（免赔额/比例/单次限额）
     * 与医院网络台账精算，不接受前端透传金额。与 {@code /reimbursement-adjustment} 的分工——
     * 那条只试算、零副作用；本条落结算、发 {@code SettleClaimCommand}。
     * </p>
     */
    @PostMapping("/{claimId}/reimbursement-settlement")
    public ApiResponse<Void> settleReimbursement(@PathVariable String claimId,
                                                 @RequestBody @Valid SettleReimbursementDTO requestVO) {
        claimCommandService.settleReimbursement(claimId, claimWebMapper.toSettleReimbursementRequest(requestVO));
        return ApiResponse.success();
    }
}
