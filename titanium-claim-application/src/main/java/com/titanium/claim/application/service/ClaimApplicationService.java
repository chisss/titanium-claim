package com.titanium.claim.application.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.command.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.claim.application.dto.ChangeClaimStatusRequestDTO;
import com.titanium.claim.application.dto.ClaimResponseDTO;
import com.titanium.claim.application.dto.CreateClaimRequestDTO;
import com.titanium.claim.application.dto.UpdateClaimRequestDTO;
import com.titanium.claim.command.ChangeClaimStatusCommand;
import com.titanium.claim.command.CreateClaimCommand;
import com.titanium.claim.command.UpdateClaimCommand;
import com.titanium.claim.enums.ClaimStatus;
import com.titanium.claim.repository.ClaimRepository;
import com.titanium.claim.service.ClaimService;
import com.titanium.claim.valueobject.ClaimAmount;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.CustomerId;
import com.titanium.claim.valueobject.PolicyId;
import com.titanium.metadata.enums.claim.ClaimEnum;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@AllArgsConstructor
@Slf4j
public class ClaimApplicationService {
    private final CommandGateway commandGateway;
    private final Repository<com.titanium.claim.aggregate.Claim> claimRepository;
    private final ClaimService claimService;
    private final PolicyService policyService;
    private final ClaimRepository claimQueryRepository;

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
                ClaimAmount.of(request.getClaimAmount())
        );
        commandGateway.sendAndWait(command);

        return claimId.value();
    }

    /**
     * 验证保单是否存在且有效
     */
    private void validatePolicy(String policyId) {
        try {
            // 调用保单系统获取保单详情
            // 注意：这里使用默认的tenantId，实际应该从请求中获取
            var policy = policyService.getPolicy(policyId, "default-tenant");

            // 验证保单状态是否有效
            if (!"ACTIVE".equals(policy.getStatus())) {
                throw new RuntimeException("保单状态无效，当前状态: " + policy.getStatus());
            }

            log.info("保单验证通过, policyId={}, status={}", policyId, policy.getStatus());
        } catch (Exception e) {
            log.error("保单验证失败, policyId={}, error={}", policyId, e.getMessage());
            throw new RuntimeException("保单验证失败: " + e.getMessage());
        }
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
                ClaimAmount.of(request.getClaimAmount())
        );
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
                request.getReason()
        );
        commandGateway.sendAndWait(command);
    }

    @Transactional(readOnly = true)
    public Optional<ClaimResponseDTO> getClaim(String claimId) {
        try {
            return Optional.of(claimRepository.load(claimId)
                    .invoke(claim -> toResponseDTO(claim)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public List<ClaimResponseDTO> getClaimsByCustomerId(String customerId) {
        // 改造：原返回 mock List.of()，现查询读侧仓储（t_claim 由 ClaimProjection 投影维护）
        return claimQueryRepository.findByCustomerId(CustomerId.of(customerId))
                .stream().map(this::toResponseDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ClaimResponseDTO> getClaimsByPolicyId(String policyId) {
        // 改造：原返回 mock List.of()，现查询读侧仓储
        return claimQueryRepository.findByPolicyId(PolicyId.of(policyId))
                .stream().map(this::toResponseDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ClaimResponseDTO> getClaimsByStatus(String status) {
        // 改造：原返回 mock List.of()，现查询读侧仓储；状态字符串按 code 解析为枚举
        return claimQueryRepository.findByStatus(ClaimStatus.fromCode(status))
                .stream().map(this::toResponseDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ClaimResponseDTO> getAllClaims() {
        // 改造：原返回 mock List.of()，现查询读侧仓储全量
        return claimQueryRepository.findAll()
                .stream().map(this::toResponseDTO).collect(Collectors.toList());
    }

    @Transactional
    public void updateClaimStatus(String claimId, String status) {
        ChangeClaimStatusRequestDTO request = new ChangeClaimStatusRequestDTO(status, "状态更新");
        changeClaimStatus(claimId, request);
    }

    // 转换为响应DTO
    private ClaimResponseDTO toResponseDTO(com.titanium.claim.aggregate.Claim claim) {
        ClaimResponseDTO response = new ClaimResponseDTO();
        response.setClaimId(claim.getClaimId().value());
        response.setCustomerId(claim.getCustomerId().value());
        response.setPolicyId(claim.getPolicyId().value());
        response.setClaimNumber(claim.getClaimNumber());
        response.setClaimType(claim.getClaimType());
        response.setIncidentDate(claim.getIncidentDate());
        response.setIncidentDescription(claim.getIncidentDescription());
        response.setClaimAmount(claim.getClaimAmount().value());
        response.setStatus(claim.getStatus());
        response.setCreatedAt(claim.getCreatedAt());
        response.setUpdatedAt(claim.getUpdatedAt());
        return response;
    }
}
