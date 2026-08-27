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
import com.titanium.claim.application.service.ClaimApplicationService;
import com.titanium.claim.web.dto.CreateClaimDTO;
import com.titanium.claim.web.dto.SettleClaimDTO;
import com.titanium.claim.web.dto.SettleDeathBenefitDTO;
import com.titanium.claim.web.dto.SubmitLossAssessmentDTO;
import com.titanium.claim.web.dto.SubmitSurveyDTO;
import com.titanium.claim.web.dto.UpdateClaimDTO;
import com.titanium.claim.web.mapper.ClaimStatisticsWebMapper;
import com.titanium.claim.web.mapper.ClaimWebMapper;
import com.titanium.claim.web.response.ClaimResponseVO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 理赔控制器（后台/端上 HTTP 入口）
 * <p>
 * 面向管理后台/端上，路径 {@code /web/v1/claims}，入参为 web 层 {@code XxxDTO}（web/dto）、出参 {@code ClaimResponseVO}，
 * <b>不再 implements ClaimApi</b>（远程契约由 {@code ClaimApiProvider} 承接）。经 {@link ClaimWebMapper} 把
 * Request VO 翻译为应用层入参 DTO，交 {@link ClaimApplicationService} 编排；读侧查询结果转 VO 返回。
 * 与 {@code ClaimApiProvider} 平行收敛到同一应用层门面，Controller 零业务逻辑。
 * </p>
 */
@RestController
@RequestMapping("/web/v1/claims")
@RequiredArgsConstructor
public class ClaimController {

    private final ClaimApplicationService claimApplicationService;
    private final ClaimWebMapper          claimWebMapper;
    private final ClaimStatisticsWebMapper claimStatisticsWebMapper;

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
                claimStatisticsWebMapper.toResponse(claimApplicationService.getStatistics(tenantId)));
    }

    /**
     * 创建理赔案件
     */
    @PostMapping
    public ResponseEntity<String> createClaim(@RequestBody @Valid CreateClaimDTO requestVO) {
        String claimId = claimApplicationService.createClaim(claimWebMapper.toCreateRequest(requestVO));
        return new ResponseEntity<>(claimId, HttpStatus.CREATED);
    }

    /**
     * 更新理赔案件
     */
    @PutMapping("/{claimId}")
    public ResponseEntity<Void> updateClaim(@PathVariable("claimId") String claimId,
                                            @RequestBody @Valid UpdateClaimDTO requestVO) {
        claimApplicationService.updateClaim(claimId, claimWebMapper.toUpdateRequest(requestVO));
        return ResponseEntity.noContent().build();
    }

    /**
     * 更新理赔状态
     */
    @PutMapping("/{claimId}/status")
    public ResponseEntity<Void> updateClaimStatus(@PathVariable("claimId") String claimId,
                                                  @RequestParam("status") String status) {
        claimApplicationService.updateClaimStatus(claimId, status);
        return ResponseEntity.noContent().build();
    }

    /**
     * 获取理赔案件详情
     */
    @GetMapping("/{claimId}")
    public ResponseEntity<ClaimResponseVO> getClaim(
            @PathVariable("claimId") String claimId,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        return claimApplicationService.getClaim(claimId, tenantId)
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
        return ResponseEntity.ok(claimApplicationService.getClaimsByCustomerId(customerId, tenantId)
                .stream().map(claimWebMapper::toVO).toList());
    }

    /**
     * 根据保单ID查询理赔案件列表
     */
    @GetMapping("/policy/{policyId}")
    public ResponseEntity<List<ClaimResponseVO>> getClaimsByPolicyId(
            @PathVariable("policyId") String policyId,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        return ResponseEntity.ok(claimApplicationService.getClaimsByPolicyId(policyId, tenantId)
                .stream().map(claimWebMapper::toVO).toList());
    }

    /**
     * 根据状态查询理赔案件列表
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<ClaimResponseVO>> getClaimsByStatus(
            @PathVariable("status") String status,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        return ResponseEntity.ok(claimApplicationService.getClaimsByStatus(status, tenantId)
                .stream().map(claimWebMapper::toVO).toList());
    }

    /**
     * 查询所有理赔案件列表
     */
    @GetMapping
    public ResponseEntity<List<ClaimResponseVO>> getAllClaims(
            @RequestHeader("X-Tenant-Id") String tenantId) {
        return ResponseEntity.ok(claimApplicationService.getAllClaims(tenantId)
                .stream().map(claimWebMapper::toVO).toList());
    }

    /**
     * 提交查勘（推进理赔阶段至 SURVEY）
     */
    @PostMapping("/{claimId}/survey")
    public ResponseEntity<Void> submitSurvey(@PathVariable("claimId") String claimId,
                                             @RequestBody @Valid SubmitSurveyDTO requestVO) {
        claimApplicationService.submitSurvey(claimId, claimWebMapper.toSurveyRequest(requestVO));
        return ResponseEntity.noContent().build();
    }

    /**
     * 提交定损（推进理赔阶段至 LOSS_ASSESS）
     */
    @PostMapping("/{claimId}/loss-assessment")
    public ResponseEntity<Void> submitLossAssessment(@PathVariable("claimId") String claimId,
                                                     @RequestBody @Valid SubmitLossAssessmentDTO requestVO) {
        claimApplicationService.submitLossAssessment(claimId, claimWebMapper.toLossAssessmentRequest(requestVO));
        return ResponseEntity.noContent().build();
    }

    /**
     * 核赔结算（APPROVED → PAID）
     */
    @PostMapping("/{claimId}/settlement")
    public ResponseEntity<Void> settleClaim(@PathVariable("claimId") String claimId,
                                            @RequestBody @Valid SettleClaimDTO requestVO) {
        claimApplicationService.settleClaim(claimId, claimWebMapper.toSettleRequest(requestVO));
        return ResponseEntity.noContent().build();
    }

    /**
     * 身故给付结算（寿险专属，APPROVED → PAID，按受益人份额一次性给付）
     */
    @PostMapping("/{claimId}/death-benefit")
    public ResponseEntity<Void> settleDeathBenefit(@PathVariable("claimId") String claimId,
                                                   @RequestBody @Valid SettleDeathBenefitDTO requestVO) {
        claimApplicationService.settleDeathBenefit(claimId, claimWebMapper.toDeathBenefitRequest(requestVO));
        return ResponseEntity.noContent().build();
    }

    /**
     * 多条件搜索理赔案件（内存过滤 + 简单分页）
     * <p>
     * 支持按理赔编号、保单ID、客户ID、状态任意组合过滤，参数均可选；结果先全量拉取再内存过滤，
     * 适用于数据量可控场景。若需高性能分页，可后续扩展 ClaimApplicationService 加 CQRS 条件查询。
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
        List<ClaimResponseVO> all = claimApplicationService.getAllClaims(tenantId).stream()
                .filter(m -> claimNo == null || claimNo.equals(m.getClaimNumber()))
                .filter(m -> policyId == null || policyId.equals(m.getPolicyId()))
                .filter(m -> customerId == null || customerId.equals(m.getCustomerId()))
                .filter(m -> status == null || status.equals(m.getStatus()))
                .map(claimWebMapper::toVO)
                .toList();
        int from = Math.min(page * size, all.size());
        int to   = Math.min(from + size, all.size());
        return ResponseEntity.ok(all.subList(from, to));
    }
}
