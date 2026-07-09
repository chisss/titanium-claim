package com.titanium.claim.web.provider;

import java.util.List;

import org.springframework.web.bind.annotation.RestController;

import com.titanium.claim.api.ClaimApi;
import com.titanium.claim.api.dto.ClaimRequestDTO;
import com.titanium.claim.api.dto.ClaimResponseDTO;
import com.titanium.claim.api.dto.SettleClaimRequestDTO;
import com.titanium.claim.api.dto.SubmitLossAssessmentRequestDTO;
import com.titanium.claim.api.dto.SubmitSurveyRequestDTO;
import com.titanium.claim.api.response.ApiResponse;
import com.titanium.claim.application.service.ClaimApplicationService;
import com.titanium.claim.web.mapper.ClaimWebMapper;

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
@RequiredArgsConstructor
public class ClaimApiProvider implements ClaimApi {

    private final ClaimApplicationService claimApplicationService;
    private final ClaimWebMapper          claimWebMapper;

    @Override
    public ApiResponse<String> createClaim(ClaimRequestDTO requestDTO, String tenantId) {
        String claimId = claimApplicationService.createClaim(claimWebMapper.toCreateDTO(requestDTO));
        return ApiResponse.success(claimId);
    }

    @Override
    public ApiResponse<Void> updateClaim(String claimId, ClaimRequestDTO requestDTO, String tenantId) {
        claimApplicationService.updateClaim(claimId, claimWebMapper.toUpdateDTO(requestDTO));
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> updateClaimStatus(String claimId, String status, String tenantId) {
        claimApplicationService.updateClaimStatus(claimId, status);
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<ClaimResponseDTO> getClaim(String claimId, String tenantId) {
        return claimApplicationService.getClaim(claimId)
                .map(claimWebMapper::toApiResponse)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error(404, "理赔案件不存在: " + claimId));
    }

    @Override
    public ApiResponse<List<ClaimResponseDTO>> getClaimsByCustomerId(String customerId, String tenantId) {
        List<ClaimResponseDTO> list = claimApplicationService.getClaimsByCustomerId(customerId)
                .stream().map(claimWebMapper::toApiResponse).toList();
        return ApiResponse.success(list);
    }

    @Override
    public ApiResponse<List<ClaimResponseDTO>> getClaimsByPolicyId(String policyId, String tenantId) {
        List<ClaimResponseDTO> list = claimApplicationService.getClaimsByPolicyId(policyId)
                .stream().map(claimWebMapper::toApiResponse).toList();
        return ApiResponse.success(list);
    }

    @Override
    public ApiResponse<List<ClaimResponseDTO>> getClaimsByStatus(String status, String tenantId) {
        List<ClaimResponseDTO> list = claimApplicationService.getClaimsByStatus(status)
                .stream().map(claimWebMapper::toApiResponse).toList();
        return ApiResponse.success(list);
    }

    @Override
    public ApiResponse<List<ClaimResponseDTO>> getAllClaims(String tenantId) {
        List<ClaimResponseDTO> list = claimApplicationService.getAllClaims()
                .stream().map(claimWebMapper::toApiResponse).toList();
        return ApiResponse.success(list);
    }

    @Override
    public ApiResponse<Void> submitSurvey(String claimId, SubmitSurveyRequestDTO requestDTO, String tenantId) {
        claimApplicationService.submitSurvey(claimId, claimWebMapper.toSurveyDTO(requestDTO));
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> submitLossAssessment(String claimId, SubmitLossAssessmentRequestDTO requestDTO,
                                                  String tenantId) {
        claimApplicationService.submitLossAssessment(claimId, claimWebMapper.toLossAssessmentDTO(requestDTO));
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> settleClaim(String claimId, SettleClaimRequestDTO requestDTO, String tenantId) {
        claimApplicationService.settleClaim(claimId, claimWebMapper.toSettleDTO(requestDTO));
        return ApiResponse.success();
    }
}
