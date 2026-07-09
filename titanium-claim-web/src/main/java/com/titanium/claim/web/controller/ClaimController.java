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

import com.titanium.claim.application.service.ClaimApplicationService;
import com.titanium.claim.web.mapper.ClaimWebMapper;
import com.titanium.claim.web.request.CreateClaimRequestVO;
import com.titanium.claim.web.request.SettleClaimRequestVO;
import com.titanium.claim.web.request.SubmitLossAssessmentRequestVO;
import com.titanium.claim.web.request.SubmitSurveyRequestVO;
import com.titanium.claim.web.request.UpdateClaimRequestVO;
import com.titanium.claim.web.response.ClaimResponseVO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 理赔控制器（后台/端上 HTTP 入口）
 * <p>
 * 面向管理后台/端上，路径 {@code /web/v1/claims}，入参为 web 层 {@code XxxRequestVO}、出参 {@code ClaimResponseVO}，
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

    /**
     * 创建理赔案件
     */
    @PostMapping
    public ResponseEntity<String> createClaim(@RequestBody @Valid CreateClaimRequestVO requestVO) {
        String claimId = claimApplicationService.createClaim(claimWebMapper.toCreateDTO(requestVO));
        return new ResponseEntity<>(claimId, HttpStatus.CREATED);
    }

    /**
     * 更新理赔案件
     */
    @PutMapping("/{claimId}")
    public ResponseEntity<Void> updateClaim(@PathVariable("claimId") String claimId,
                                            @RequestBody @Valid UpdateClaimRequestVO requestVO) {
        claimApplicationService.updateClaim(claimId, claimWebMapper.toUpdateDTO(requestVO));
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
    public ResponseEntity<ClaimResponseVO> getClaim(@PathVariable("claimId") String claimId) {
        return claimApplicationService.getClaim(claimId)
                .map(claimWebMapper::toVO)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 根据客户ID查询理赔案件列表
     */
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<ClaimResponseVO>> getClaimsByCustomerId(
            @PathVariable("customerId") String customerId) {
        return ResponseEntity.ok(claimApplicationService.getClaimsByCustomerId(customerId)
                .stream().map(claimWebMapper::toVO).toList());
    }

    /**
     * 根据保单ID查询理赔案件列表
     */
    @GetMapping("/policy/{policyId}")
    public ResponseEntity<List<ClaimResponseVO>> getClaimsByPolicyId(@PathVariable("policyId") String policyId) {
        return ResponseEntity.ok(claimApplicationService.getClaimsByPolicyId(policyId)
                .stream().map(claimWebMapper::toVO).toList());
    }

    /**
     * 根据状态查询理赔案件列表
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<ClaimResponseVO>> getClaimsByStatus(@PathVariable("status") String status) {
        return ResponseEntity.ok(claimApplicationService.getClaimsByStatus(status)
                .stream().map(claimWebMapper::toVO).toList());
    }

    /**
     * 查询所有理赔案件列表
     */
    @GetMapping
    public ResponseEntity<List<ClaimResponseVO>> getAllClaims() {
        return ResponseEntity.ok(claimApplicationService.getAllClaims()
                .stream().map(claimWebMapper::toVO).toList());
    }

    /**
     * 提交查勘（推进理赔阶段至 SURVEY）
     */
    @PostMapping("/{claimId}/survey")
    public ResponseEntity<Void> submitSurvey(@PathVariable("claimId") String claimId,
                                             @RequestBody @Valid SubmitSurveyRequestVO requestVO) {
        claimApplicationService.submitSurvey(claimId, claimWebMapper.toSurveyDTO(requestVO));
        return ResponseEntity.noContent().build();
    }

    /**
     * 提交定损（推进理赔阶段至 LOSS_ASSESS）
     */
    @PostMapping("/{claimId}/loss-assessment")
    public ResponseEntity<Void> submitLossAssessment(@PathVariable("claimId") String claimId,
                                                     @RequestBody @Valid SubmitLossAssessmentRequestVO requestVO) {
        claimApplicationService.submitLossAssessment(claimId, claimWebMapper.toLossAssessmentDTO(requestVO));
        return ResponseEntity.noContent().build();
    }

    /**
     * 核赔结算（APPROVED → PAID）
     */
    @PostMapping("/{claimId}/settlement")
    public ResponseEntity<Void> settleClaim(@PathVariable("claimId") String claimId,
                                            @RequestBody @Valid SettleClaimRequestVO requestVO) {
        claimApplicationService.settleClaim(claimId, claimWebMapper.toSettleDTO(requestVO));
        return ResponseEntity.noContent().build();
    }
}
