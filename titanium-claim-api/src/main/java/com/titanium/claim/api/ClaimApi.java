package com.titanium.claim.api;

import com.titanium.claim.api.dto.ClaimRequestDTO;
import com.titanium.claim.api.dto.ClaimResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 理赔API
 * 定义其他项目调用的Feign接口
 */
@FeignClient(name = "titanium-claim")
@RequestMapping("/api/claims")
public interface ClaimApi {
    /**
     * 创建理赔
     */
    @PostMapping
    String createClaim(@RequestBody @Valid ClaimRequestDTO requestDTO);

    /**
     * 更新理赔
     */
    @PutMapping("/{claimId}")
    void updateClaim(@PathVariable String claimId, @RequestBody @Valid ClaimRequestDTO requestDTO);

    /**
     * 更新理赔状态
     */
    @PutMapping("/{claimId}/status")
    void updateClaimStatus(@PathVariable String claimId, @RequestParam String status);

    /**
     * 获取理赔详情
     */
    @GetMapping("/{claimId}")
    ClaimResponseDTO getClaim(@PathVariable String claimId);

    /**
     * 根据客户ID查询理赔列表
     */
    @GetMapping("/customer/{customerId}")
    List<ClaimResponseDTO> getClaimsByCustomerId(@PathVariable String customerId);

    /**
     * 根据保单ID查询理赔列表
     */
    @GetMapping("/policy/{policyId}")
    List<ClaimResponseDTO> getClaimsByPolicyId(@PathVariable String policyId);

    /**
     * 根据状态查询理赔列表
     */
    @GetMapping("/status/{status}")
    List<ClaimResponseDTO> getClaimsByStatus(@PathVariable String status);

    /**
     * 查询所有理赔列表
     */
    @GetMapping
    List<ClaimResponseDTO> getAllClaims();
}
