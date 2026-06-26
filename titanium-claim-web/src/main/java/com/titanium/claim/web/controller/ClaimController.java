package com.titanium.claim.web.controller;

import com.titanium.claim.api.ClaimApi;
import com.titanium.claim.api.dto.ClaimRequestDTO;
import com.titanium.claim.api.dto.ClaimResponseDTO;
import com.titanium.claim.application.service.ClaimApplicationService;
import com.titanium.claim.web.mapper.ClaimWebMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 理赔控制器
 * 实现 ClaimApi 接口，为管理后台提供访问。
 * 仅负责对外 DTO 与应用层 DTO 的转换及服务编排调用，不含业务逻辑。
 */
@RestController
@RequestMapping("/web/claims")
@RequiredArgsConstructor
public class ClaimController implements ClaimApi {

    private final ClaimApplicationService claimApplicationService;
    private final ClaimWebMapper claimWebMapper;

    @Override
    public String createClaim(@Valid ClaimRequestDTO requestDTO) {
        return claimApplicationService.createClaim(claimWebMapper.toCreateDTO(requestDTO));
    }

    @Override
    public void updateClaim(String claimId, @Valid ClaimRequestDTO requestDTO) {
        claimApplicationService.updateClaim(claimId, claimWebMapper.toUpdateDTO(requestDTO));
    }

    @Override
    public void updateClaimStatus(String claimId, String status) {
        claimApplicationService.updateClaimStatus(claimId, status);
    }

    @Override
    public ClaimResponseDTO getClaim(String claimId) {
        return claimApplicationService.getClaim(claimId)
                .map(claimWebMapper::toApiResponse)
                .orElse(null);
    }

    @Override
    public List<ClaimResponseDTO> getClaimsByCustomerId(String customerId) {
        return claimApplicationService.getClaimsByCustomerId(customerId)
                .stream().map(claimWebMapper::toApiResponse).toList();
    }

    @Override
    public List<ClaimResponseDTO> getClaimsByPolicyId(String policyId) {
        return claimApplicationService.getClaimsByPolicyId(policyId)
                .stream().map(claimWebMapper::toApiResponse).toList();
    }

    @Override
    public List<ClaimResponseDTO> getClaimsByStatus(String status) {
        return claimApplicationService.getClaimsByStatus(status)
                .stream().map(claimWebMapper::toApiResponse).toList();
    }

    @Override
    public List<ClaimResponseDTO> getAllClaims() {
        return claimApplicationService.getAllClaims()
                .stream().map(claimWebMapper::toApiResponse).toList();
    }
}
