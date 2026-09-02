package com.titanium.claim.api;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import com.titanium.claim.api.request.ClaimRequest;
import com.titanium.claim.api.request.FlagClaimAlertRequest;
import com.titanium.claim.api.request.RejectClaimRequest;
import com.titanium.claim.api.request.SettleClaimRequest;
import com.titanium.claim.api.request.SettleDeathBenefitRequest;
import com.titanium.claim.api.request.SettleDisabilityBenefitRequest;
import com.titanium.claim.api.request.SubmitLossAssessmentRequest;
import com.titanium.claim.api.request.SubmitSurveyRequest;
import com.titanium.claim.api.response.ClaimResponse;
import com.titanium.metadata.response.ApiResponse;

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
@FeignClient(name = "titanium-claim", contextId = "claimApi", path = "/api/v1/claims")
public interface ClaimApi {

    /**
     * 创建理赔案件
     *
     * @param requestDTO 理赔请求 DTO
     * @param tenantId 租户ID
     * @return 理赔案件ID
     */
    @PostMapping
    ApiResponse<String> createClaim(@RequestBody @Valid ClaimRequest requestDTO,
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
                                  @RequestBody @Valid ClaimRequest requestDTO,
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
    ApiResponse<ClaimResponse> getClaim(@PathVariable("claimId") String claimId,
                                           @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 根据客户ID查询理赔案件列表
     *
     * @param customerId 客户ID
     * @param tenantId 租户ID
     * @return 理赔案件列表
     */
    @GetMapping("/customer/{customerId}")
    ApiResponse<List<ClaimResponse>> getClaimsByCustomerId(@PathVariable("customerId") String customerId,
                                                              @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 根据保单ID查询理赔案件列表
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 理赔案件列表
     */
    @GetMapping("/policy/{policyId}")
    ApiResponse<List<ClaimResponse>> getClaimsByPolicyId(@PathVariable("policyId") String policyId,
                                                            @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 根据状态查询理赔案件列表
     *
     * @param status 状态码
     * @param tenantId 租户ID
     * @return 理赔案件列表
     */
    @GetMapping("/status/{status}")
    ApiResponse<List<ClaimResponse>> getClaimsByStatus(@PathVariable("status") String status,
                                                          @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 查询所有理赔案件列表
     *
     * @param tenantId 租户ID
     * @return 理赔案件列表
     */
    @GetMapping
    ApiResponse<List<ClaimResponse>> getAllClaims(@RequestHeader("X-Tenant-Id") String tenantId);

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
                                   @RequestBody @Valid SubmitSurveyRequest requestDTO,
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
                                           @RequestBody @Valid SubmitLossAssessmentRequest requestDTO,
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
                                  @RequestBody @Valid SettleClaimRequest requestDTO,
                                  @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 身故给付结算（寿险专属，APPROVED → PAID，按受益人份额一次性给付）
     * <p>
     * 给付总额由下游按保单基本保额精算（CLAIM-2），入参只承载保单定位键与受益人份额规格，
     * 禁止调用方透传金额。
     * </p>
     *
     * @param claimId 理赔案件ID
     * @param requestDTO 身故给付结算请求
     * @param tenantId 租户ID
     * @return 空响应
     */
    @PostMapping("/{claimId}/death-benefit")
    ApiResponse<Void> settleDeathBenefit(@PathVariable("claimId") String claimId,
                                         @RequestBody @Valid SettleDeathBenefitRequest requestDTO,
                                         @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 全残给付结算（寿险/意外险专属，CLAIM-6，APPROVED → PAID，按受益人份额一次性给付）
     * <p>
     * 给付总额由下游按保单条款精算（基本保额、或账户价值与基本保额孰高），入参只承载保单定位键、
     * 全残证据与受益人份额规格，禁止调用方透传金额。
     * </p>
     *
     * @param claimId 理赔案件ID
     * @param requestDTO 全残给付结算请求
     * @param tenantId 租户ID
     * @return 空响应
     */
    @PostMapping("/{claimId}/disability-benefit")
    ApiResponse<Void> settleDisabilityBenefit(@PathVariable("claimId") String claimId,
                                              @RequestBody @Valid SettleDisabilityBenefitRequest requestDTO,
                                              @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 打标警示标记（手动打标/规则引擎回写：反欺诈警示 + 统计口径标记）
     * <p>
     * 类型经 {@code AlertType} code 承载（落库枚举化，红线 20），聚合根按类型合并去重（幂等），
     * 投影至读模型 {@code alert_flags} 列供快赔通道判据「无欺诈警示标记」使用。
     * </p>
     *
     * @param claimId 理赔案件ID
     * @param requestDTO 警示标记请求
     * @param tenantId 租户ID
     * @return 空响应
     */
    @PostMapping("/{claimId}/alert-flags")
    ApiResponse<Void> flagClaimAlert(@PathVariable("claimId") String claimId,
                                     @RequestBody @Valid FlagClaimAlertRequest requestDTO,
                                     @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 快赔自动核赔（小额快赔通道，产品文档 §2.10：规则匹配 + 金额/状态/无欺诈警示判据
     * 全过自动核赔（APPROVED → 结算）并打快赔统计标记）
     *
     * @param claimId 理赔案件ID
     * @param tenantId 租户ID
     * @return 空响应
     */
    @PostMapping("/{claimId}/quick-pay")
    ApiResponse<Void> quickPay(@PathVariable("claimId") String claimId,
                               @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 拒赔（核赔否决，PENDING/PROCESSING → REJECTED）
     *
     * @param claimId 理赔案件ID
     * @param requestDTO 拒赔请求（原因按枚举 code 承载）
     * @param tenantId 租户ID
     * @return 空响应
     */
    @PostMapping("/{claimId}/reject")
    ApiResponse<Void> rejectClaim(@PathVariable("claimId") String claimId,
                                  @RequestBody @Valid RejectClaimRequest requestDTO,
                                  @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 结案（归档，PAID/REJECTED → CLOSED）
     *
     * @param claimId 理赔案件ID
     * @param tenantId 租户ID
     * @return 空响应
     */
    @PostMapping("/{claimId}/close")
    ApiResponse<Void> closeClaim(@PathVariable("claimId") String claimId,
                                 @RequestHeader("X-Tenant-Id") String tenantId);
}
