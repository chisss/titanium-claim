package com.titanium.claim.web.provider;

import java.util.List;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.claim.api.ClaimApi;
import com.titanium.claim.api.request.ClaimRequest;
import com.titanium.claim.api.request.FlagClaimAlertRequest;
import com.titanium.claim.api.request.RejectClaimRequest;
import com.titanium.claim.api.request.SettleClaimRequest;
import com.titanium.claim.api.request.SettleDeathBenefitRequest;
import com.titanium.claim.api.request.SettleDisabilityBenefitRequest;
import com.titanium.claim.api.request.SubmitLossAssessmentRequest;
import com.titanium.claim.api.request.SubmitSurveyRequest;
import com.titanium.claim.api.response.ClaimResponse;
import com.titanium.claim.application.command.ClaimCommandService;
import com.titanium.claim.application.query.ClaimAppQueryService;
import com.titanium.claim.web.mapper.ClaimWebMapper;
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
    public ApiResponse<String> createClaim(ClaimRequest requestDTO, String tenantId) {
        String claimId = claimCommandService.createClaim(claimWebMapper.toCreateRequest(requestDTO));
        return ApiResponse.success(claimId);
    }

    @Override
    public ApiResponse<Void> updateClaim(String claimId, ClaimRequest requestDTO, String tenantId) {
        claimCommandService.updateClaim(claimId, claimWebMapper.toUpdateRequest(requestDTO));
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> updateClaimStatus(String claimId, String status, String tenantId) {
        claimCommandService.updateClaimStatus(claimId, status);
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<ClaimResponse> getClaim(String claimId, String tenantId) {
        return claimAppQueryService.getClaim(claimId, tenantId)
                .map(claimWebMapper::toApiResponse)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error(ClaimErrorCode.CLAIM_NOT_EXIST, "理赔案件不存在: " + claimId));
    }

    @Override
    public ApiResponse<List<ClaimResponse>> getClaimsByCustomerId(String customerId, String tenantId) {
        List<ClaimResponse> list = claimAppQueryService.getClaimsByCustomerId(customerId, tenantId)
                .stream().map(claimWebMapper::toApiResponse).toList();
        return ApiResponse.success(list);
    }

    @Override
    public ApiResponse<List<ClaimResponse>> getClaimsByPolicyId(String policyId, String tenantId) {
        List<ClaimResponse> list = claimAppQueryService.getClaimsByPolicyId(policyId, tenantId)
                .stream().map(claimWebMapper::toApiResponse).toList();
        return ApiResponse.success(list);
    }

    @Override
    public ApiResponse<List<ClaimResponse>> getClaimsByStatus(String status, String tenantId) {
        List<ClaimResponse> list = claimAppQueryService.getClaimsByStatus(status, tenantId)
                .stream().map(claimWebMapper::toApiResponse).toList();
        return ApiResponse.success(list);
    }

    @Override
    public ApiResponse<List<ClaimResponse>> getAllClaims(String tenantId) {
        List<ClaimResponse> list = claimAppQueryService.getAllClaims(tenantId)
                .stream().map(claimWebMapper::toApiResponse).toList();
        return ApiResponse.success(list);
    }

    @Override
    public ApiResponse<Void> submitSurvey(String claimId, SubmitSurveyRequest requestDTO, String tenantId) {
        claimCommandService.submitSurvey(claimId, claimWebMapper.toSurveyRequest(requestDTO));
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> submitLossAssessment(String claimId, SubmitLossAssessmentRequest requestDTO,
                                                  String tenantId) {
        claimCommandService.submitLossAssessment(claimId, claimWebMapper.toLossAssessmentRequest(requestDTO));
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> settleClaim(String claimId, SettleClaimRequest requestDTO, String tenantId) {
        claimCommandService.settleClaim(claimId, claimWebMapper.toSettleRequest(requestDTO));
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> settleDeathBenefit(String claimId, SettleDeathBenefitRequest requestDTO,
                                                String tenantId) {
        claimCommandService.settleDeathBenefit(claimId, claimWebMapper.toDeathBenefitRequest(requestDTO));
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> settleDisabilityBenefit(String claimId, SettleDisabilityBenefitRequest requestDTO,
                                                     String tenantId) {
        claimCommandService.settleDisabilityBenefit(claimId, claimWebMapper.toDisabilityBenefitRequest(requestDTO));
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> flagClaimAlert(String claimId, FlagClaimAlertRequest requestDTO, String tenantId) {
        claimCommandService.flagAlert(claimId, claimWebMapper.toFlagAlertRequest(requestDTO));
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> quickPay(String claimId, String tenantId) {
        claimCommandService.quickPay(claimId);
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> rejectClaim(String claimId, RejectClaimRequest requestDTO, String tenantId) {
        claimCommandService.rejectClaim(claimId, claimWebMapper.toRejectRequest(requestDTO));
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> closeClaim(String claimId, String tenantId) {
        claimCommandService.closeClaim(claimId);
        return ApiResponse.success();
    }
}
