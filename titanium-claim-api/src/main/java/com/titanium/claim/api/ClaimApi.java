package com.titanium.claim.api;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.titanium.claim.api.dto.ClaimRequestDTO;
import com.titanium.claim.api.dto.ClaimResponseDTO;
import com.titanium.claim.api.dto.SettleClaimRequestDTO;
import com.titanium.claim.api.dto.SubmitLossAssessmentRequestDTO;
import com.titanium.claim.api.dto.SubmitSurveyRequestDTO;
import com.titanium.claim.api.response.ApiResponse;

import jakarta.validation.Valid;

/**
 * 理赔聚合对外契约（Feign）
 * <p>
 * 命名主键为聚合根 {@code Claim}，承载理赔案件的跨服务远程调用。契约路径遵从内部服务远程调用规约
 * {@code /api/v1/claims}，由 web 层 {@code ClaimApiProvider} 实现，路径不得篡改。所有方法透传
 * {@code X-Tenant-Id} 请求头贯穿多租户上下文，入出参一律使用 api 层 DTO（领域枚举以 String 承载）。
 * </p>
 * <p>
 * 同域多个 {@code @FeignClient} 的 {@code name} 相同，必须各配唯一 {@code contextId}，否则
 * Spring 启动报「Multiple @FeignClient with the same name」Bean 冲突。原 {@code ClaimClient}
 * （api/client）为老式命名的冗余契约，已合并至本接口并删除。
 * </p>
 */
@FeignClient(name = "titanium-claim", contextId = "claimApi")
@RequestMapping("/api/v1/claims")
public interface ClaimApi {

    /**
     * 创建理赔案件
     *
     * @param requestDTO 理赔请求 DTO
     * @param tenantId 租户ID
     * @return 理赔案件ID
     */
    @PostMapping
    ApiResponse<String> createClaim(@RequestBody @Valid ClaimRequestDTO requestDTO,
                                    @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 更新理赔案件
     *
     * @param claimId 理赔案件ID
     * @param requestDTO 理赔请求 DTO
     * @param tenantId 租户ID
     * @return 空响应
     */
    @PutMapping("/{claimId}")
    ApiResponse<Void> updateClaim(@PathVariable("claimId") String claimId,
                                  @RequestBody @Valid ClaimRequestDTO requestDTO,
                                  @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 更新理赔状态
     *
     * @param claimId 理赔案件ID
     * @param status 目标状态码
     * @param tenantId 租户ID
     * @return 空响应
     */
    @PutMapping("/{claimId}/status")
    ApiResponse<Void> updateClaimStatus(@PathVariable("claimId") String claimId,
                                        @RequestParam("status") String status,
                                        @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 获取理赔案件详情（跨域集成用）
     *
     * @param claimId 理赔案件ID
     * @param tenantId 租户ID
     * @return 理赔详情，不存在时 code=404
     */
    @GetMapping("/{claimId}")
    ApiResponse<ClaimResponseDTO> getClaim(@PathVariable("claimId") String claimId,
                                           @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 根据客户ID查询理赔案件列表
     *
     * @param customerId 客户ID
     * @param tenantId 租户ID
     * @return 理赔案件列表
     */
    @GetMapping("/customer/{customerId}")
    ApiResponse<List<ClaimResponseDTO>> getClaimsByCustomerId(@PathVariable("customerId") String customerId,
                                                              @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 根据保单ID查询理赔案件列表
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 理赔案件列表
     */
    @GetMapping("/policy/{policyId}")
    ApiResponse<List<ClaimResponseDTO>> getClaimsByPolicyId(@PathVariable("policyId") String policyId,
                                                            @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 根据状态查询理赔案件列表
     *
     * @param status 状态码
     * @param tenantId 租户ID
     * @return 理赔案件列表
     */
    @GetMapping("/status/{status}")
    ApiResponse<List<ClaimResponseDTO>> getClaimsByStatus(@PathVariable("status") String status,
                                                          @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 查询所有理赔案件列表
     *
     * @param tenantId 租户ID
     * @return 理赔案件列表
     */
    @GetMapping
    ApiResponse<List<ClaimResponseDTO>> getAllClaims(@RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 提交查勘（推进理赔阶段至 SURVEY）
     *
     * @param claimId 理赔案件ID
     * @param requestDTO 查勘请求 DTO
     * @param tenantId 租户ID
     * @return 空响应
     */
    @PostMapping("/{claimId}/survey")
    ApiResponse<Void> submitSurvey(@PathVariable("claimId") String claimId,
                                   @RequestBody @Valid SubmitSurveyRequestDTO requestDTO,
                                   @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 提交定损（推进理赔阶段至 LOSS_ASSESS）
     *
     * @param claimId 理赔案件ID
     * @param requestDTO 定损请求 DTO
     * @param tenantId 租户ID
     * @return 空响应
     */
    @PostMapping("/{claimId}/loss-assessment")
    ApiResponse<Void> submitLossAssessment(@PathVariable("claimId") String claimId,
                                           @RequestBody @Valid SubmitLossAssessmentRequestDTO requestDTO,
                                           @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 核赔结算（APPROVED → PAID）
     *
     * @param claimId 理赔案件ID
     * @param requestDTO 结算请求 DTO
     * @param tenantId 租户ID
     * @return 空响应
     */
    @PostMapping("/{claimId}/settlement")
    ApiResponse<Void> settleClaim(@PathVariable("claimId") String claimId,
                                  @RequestBody @Valid SettleClaimRequestDTO requestDTO,
                                  @RequestHeader("X-Tenant-Id") String tenantId);
}
