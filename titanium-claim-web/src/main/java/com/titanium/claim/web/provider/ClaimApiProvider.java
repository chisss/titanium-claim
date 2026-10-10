package com.titanium.claim.web.provider;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.claim.api.ClaimApi;
import com.titanium.claim.api.request.assessment.SubmitLossAssessmentRequest;
import com.titanium.claim.api.request.assessment.SubmitSurveyRequest;
import com.titanium.claim.api.request.issuance.ClaimRequest;
import com.titanium.claim.api.request.maintenance.FlagClaimAlertRequest;
import com.titanium.claim.api.request.maintenance.RejectClaimRequest;
import com.titanium.claim.api.request.settlement.SettleClaimRequest;
import com.titanium.claim.api.request.settlement.SettleDeathBenefitRequest;
import com.titanium.claim.api.request.settlement.SettleDisabilityBenefitRequest;
import com.titanium.claim.api.request.settlement.SettleReimbursementRequest;
import com.titanium.claim.api.response.ClaimResponse;
import com.titanium.claim.application.command.ClaimCommandService;
import com.titanium.claim.application.query.ClaimAppQueryService;
import com.titanium.claim.web.mapper.ClaimWebMapper;
import com.titanium.common.context.RequestContextHolder;
import com.titanium.metadata.errorcode.ClaimErrorCode;
import com.titanium.metadata.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * 理赔契约实现（Provider）
 * <p>
 * 承接 {@link ClaimApi} Feign 契约，面向其它微服务的远程调用。路径由 {@link ClaimApi} 的
 * {@code @RequestMapping("/api/v1/claims")} 唯一定义，本类通过 {@code implements} 继承，
 * <b>不重复标注、不篡改</b>。职责仅为协议转换（api DTO → 应用层入参）+ 调用应用层门面，零业务逻辑；
 * 查询未命中返回 {@code code=404}。与面向后台/端上的 {@code ClaimController} 平行收敛到同一
 * {@link ClaimCommandService} 与 {@link ClaimAppQueryService}。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/claims")
@RequiredArgsConstructor
public class ClaimApiProvider implements ClaimApi {

    private final ClaimCommandService  claimCommandService;
    private final ClaimAppQueryService claimAppQueryService;
    private final ClaimWebMapper          claimWebMapper;

    @Override
    public ApiResponse<String> createClaim(ClaimRequest requestDTO) {
        String claimId = claimCommandService.createClaim(claimWebMapper.toCreateRequest(requestDTO));
        return ApiResponse.success(claimId);
    }

    @Override
    public ApiResponse<Void> updateClaim(String claimId, ClaimRequest requestDTO) {
        claimCommandService.updateClaim(claimId, claimWebMapper.toUpdateRequest(requestDTO));
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> updateClaimStatus(String claimId, String status) {
        claimCommandService.updateClaimStatus(claimId, status);
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<ClaimResponse> getClaim(String claimId) {
        return claimAppQueryService.getClaim(claimId, RequestContextHolder.requireTenantId())
                .map(claimWebMapper::toApiResponse)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error(ClaimErrorCode.CLAIM_NOT_EXIST, "理赔案件不存在: " + claimId));
    }

    @Override
    public ApiResponse<List<ClaimResponse>> getClaimsByCustomerId(String customerId) {
        List<ClaimResponse> list = claimAppQueryService.getClaimsByCustomerId(customerId, RequestContextHolder.requireTenantId())
                .stream().map(claimWebMapper::toApiResponse).toList();
        return ApiResponse.success(list);
    }

    @Override
    public ApiResponse<List<ClaimResponse>> getClaimsByPolicyId(String policyId) {
        List<ClaimResponse> list = claimAppQueryService.getClaimsByPolicyId(policyId, RequestContextHolder.requireTenantId())
                .stream().map(claimWebMapper::toApiResponse).toList();
        return ApiResponse.success(list);
    }

    /**
     * 按车辆/标的检索历史赔案（NCD 反算取数出口，G11/g11-03）
     * <p>
     * VIN 优先、标的ID兜底（两者皆空由读侧抛参数异常 → 全局兜底转业务错误响应）；
     * 区间为出险日期 {@code [incidentFrom, incidentTo)} 半开，拒赔案件已被读侧排除。
     * </p>
     */
    @Override
    public ApiResponse<List<ClaimResponse>> getClaimsBySubjectHistory(String vin, String subjectId,
            LocalDateTime incidentFrom, LocalDateTime incidentTo) {
        List<ClaimResponse> list = claimAppQueryService
                .getClaimHistoryBySubject(vin, subjectId, incidentFrom, incidentTo, RequestContextHolder.requireTenantId())
                .stream().map(claimWebMapper::toApiResponse).toList();
        return ApiResponse.success(list);
    }

    @Override
    public ApiResponse<List<ClaimResponse>> getClaimsByStatus(String status) {
        List<ClaimResponse> list = claimAppQueryService.getClaimsByStatus(status, RequestContextHolder.requireTenantId())
                .stream().map(claimWebMapper::toApiResponse).toList();
        return ApiResponse.success(list);
    }

    @Override
    public ApiResponse<List<ClaimResponse>> getAllClaims() {
        List<ClaimResponse> list = claimAppQueryService.getAllClaims(RequestContextHolder.requireTenantId())
                .stream().map(claimWebMapper::toApiResponse).toList();
        return ApiResponse.success(list);
    }

    @Override
    public ApiResponse<Void> submitSurvey(String claimId, SubmitSurveyRequest requestDTO) {
        claimCommandService.submitSurvey(claimId, claimWebMapper.toSurveyRequest(requestDTO));
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> submitLossAssessment(String claimId, SubmitLossAssessmentRequest requestDTO) {
        claimCommandService.submitLossAssessment(claimId, claimWebMapper.toLossAssessmentRequest(requestDTO));
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> settleClaim(String claimId, SettleClaimRequest requestDTO) {
        claimCommandService.settleClaim(claimId, claimWebMapper.toSettleRequest(requestDTO));
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> settleDeathBenefit(String claimId, SettleDeathBenefitRequest requestDTO) {
        claimCommandService.settleDeathBenefit(claimId, claimWebMapper.toDeathBenefitRequest(requestDTO));
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> settleDisabilityBenefit(String claimId, SettleDisabilityBenefitRequest requestDTO) {
        claimCommandService.settleDisabilityBenefit(claimId, claimWebMapper.toDisabilityBenefitRequest(requestDTO));
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> flagClaimAlert(String claimId, FlagClaimAlertRequest requestDTO) {
        claimCommandService.flagAlert(claimId, claimWebMapper.toFlagAlertRequest(requestDTO));
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> quickPay(String claimId) {
        claimCommandService.quickPay(claimId);
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> settleReimbursement(String claimId, SettleReimbursementRequest requestDTO) {
        claimCommandService.settleReimbursement(claimId,
                claimWebMapper.toSettleReimbursementRequest(requestDTO));
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> rejectClaim(String claimId, RejectClaimRequest requestDTO) {
        claimCommandService.rejectClaim(claimId, claimWebMapper.toRejectRequest(requestDTO));
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> closeClaim(String claimId) {
        claimCommandService.closeClaim(claimId);
        return ApiResponse.success();
    }
}
