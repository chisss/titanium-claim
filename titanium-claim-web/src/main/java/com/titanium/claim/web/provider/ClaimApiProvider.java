package com.titanium.claim.web.provider;

import java.util.List;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.claim.api.ClaimApi;
import com.titanium.claim.api.request.ClaimRequest;
import com.titanium.claim.api.request.SettleClaimRequest;
import com.titanium.claim.api.request.SubmitLossAssessmentRequest;
import com.titanium.claim.api.request.SubmitSurveyRequest;
import com.titanium.claim.api.response.ClaimResponse;
import com.titanium.claim.application.service.ClaimApplicationService;
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
 * {@link ClaimApplicationService}。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/claims")
@RequiredArgsConstructor
public class ClaimApiProvider implements ClaimApi {

    private final ClaimApplicationService claimApplicationService;
    private final ClaimWebMapper          claimWebMapper;

    @Override
    public ApiResponse<String> createClaim(ClaimRequest requestDTO, String tenantId) {
        String claimId = claimApplicationService.createClaim(claimWebMapper.toCreateRequest(requestDTO));
        return ApiResponse.success(claimId);
    }

    @Override
    public ApiResponse<Void> updateClaim(String claimId, ClaimRequest requestDTO, String tenantId) {
        claimApplicationService.updateClaim(claimId, claimWebMapper.toUpdateRequest(requestDTO));
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> updateClaimStatus(String claimId, String status, String tenantId) {
        claimApplicationService.updateClaimStatus(claimId, status);
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<ClaimResponse> getClaim(String claimId, String tenantId) {
        return claimApplicationService.getClaim(claimId, tenantId)
                .map(claimWebMapper::toApiResponse)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error(ClaimErrorCode.CLAIM_NOT_EXIST, "理赔案件不存在: " + claimId));
    }

    @Override
    public ApiResponse<List<ClaimResponse>> getClaimsByCustomerId(String customerId, String tenantId) {
        List<ClaimResponse> list = claimApplicationService.getClaimsByCustomerId(customerId, tenantId)
                .stream().map(claimWebMapper::toApiResponse).toList();
        return ApiResponse.success(list);
    }

    @Override
    public ApiResponse<List<ClaimResponse>> getClaimsByPolicyId(String policyId, String tenantId) {
        List<ClaimResponse> list = claimApplicationService.getClaimsByPolicyId(policyId, tenantId)
                .stream().map(claimWebMapper::toApiResponse).toList();
        return ApiResponse.success(list);
    }

    @Override
    public ApiResponse<List<ClaimResponse>> getClaimsByStatus(String status, String tenantId) {
        List<ClaimResponse> list = claimApplicationService.getClaimsByStatus(status, tenantId)
                .stream().map(claimWebMapper::toApiResponse).toList();
        return ApiResponse.success(list);
    }

    @Override
    public ApiResponse<List<ClaimResponse>> getAllClaims(String tenantId) {
        List<ClaimResponse> list = claimApplicationService.getAllClaims(tenantId)
                .stream().map(claimWebMapper::toApiResponse).toList();
        return ApiResponse.success(list);
    }

    @Override
    public ApiResponse<Void> submitSurvey(String claimId, SubmitSurveyRequest requestDTO, String tenantId) {
        claimApplicationService.submitSurvey(claimId, claimWebMapper.toSurveyRequest(requestDTO));
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> submitLossAssessment(String claimId, SubmitLossAssessmentRequest requestDTO,
                                                  String tenantId) {
        claimApplicationService.submitLossAssessment(claimId, claimWebMapper.toLossAssessmentRequest(requestDTO));
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> settleClaim(String claimId, SettleClaimRequest requestDTO, String tenantId) {
        claimApplicationService.settleClaim(claimId, claimWebMapper.toSettleRequest(requestDTO));
        return ApiResponse.success();
    }
}
