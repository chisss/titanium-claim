package com.titanium.claim.api;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

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
import com.titanium.metadata.enums.Reserved;
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
     * @return 理赔案件ID
     */
    @PostMapping
    @Reserved("g16-04: 跨域零消费者（管理面经 web 层）；登记见 docs/技术文档/契约登记表.md")
    ApiResponse<String> createClaim(@RequestBody @Valid ClaimRequest requestDTO);

    /**
     * 更新理赔案件
     *
     * @param claimId 理赔案件ID
     * @param requestDTO 理赔请求 DTO
     * @return 空响应
     */
    @PutMapping("/{claimId}")
    @Reserved("g16-04: 跨域零消费者（管理面经 web 层）；登记见 docs/技术文档/契约登记表.md")
    ApiResponse<Void> updateClaim(@PathVariable String claimId,
                                  @RequestBody @Valid ClaimRequest requestDTO);

    /**
     * 更新理赔状态
     *
     * @param claimId 理赔案件ID
     * @param status 目标状态码
     * @return 空响应
     */
    @PutMapping("/{claimId}/status")
    @Reserved("g16-04: 跨域零消费者（管理面经 web 层）；登记见 docs/技术文档/契约登记表.md")
    ApiResponse<Void> updateClaimStatus(@PathVariable String claimId,
                                        @RequestParam String status);

    /**
     * 获取理赔案件详情（跨域集成用）
     *
     * @param claimId 理赔案件ID
     * @return 理赔详情，不存在时 code=404
     */
    @GetMapping("/{claimId}")
    @Reserved("g16-04: 跨域零消费者（管理面经 web 层）；登记见 docs/技术文档/契约登记表.md")
    ApiResponse<ClaimResponse> getClaim(@PathVariable String claimId);

    /**
     * 根据客户ID查询理赔案件列表
     *
     * @param customerId 客户ID
     * @return 理赔案件列表
     */
    @GetMapping("/customer/{customerId}")
    @Reserved("g16-04: 跨域零消费者（管理面经 web 层）；登记见 docs/技术文档/契约登记表.md")
    ApiResponse<List<ClaimResponse>> getClaimsByCustomerId(@PathVariable String customerId);

    /**
     * 根据保单ID查询理赔案件列表
     *
     * @param policyId 保单ID
     * @return 理赔案件列表
     */
    @GetMapping("/policy/{policyId}")
    ApiResponse<List<ClaimResponse>> getClaimsByPolicyId(@PathVariable String policyId);

    /**
     * 按车辆/标的检索历史赔案（出险日期区间内，排除拒赔）—— G11/g11-03
     * <p>
     * 原 product 域「NCD 反算」的取数出口：按被保车辆历史赔案次数反算无赔款优待系数。
     * 🔴 <b>m27-04/PKG-04 起 product 域改为消费理赔域赔案事件、维护本地投影
     * （{@code t_product_claim_history_view}），本端点跨域零消费者</b>——保留作为理赔域
     * 历史赔案检索能力的服务间出口（{@code @Reserved} 已登记，见契约登记表 §二）。
     * 语义不变：{@code vin} 与 {@code subjectId} 至少提供一个（VIN 优先——它是跨保单的车辆身份，
     * 续保换单后 policyId/subjectId 均变，唯 VIN 不变）；区间为出险日期 {@code [incidentFrom, incidentTo)}
     * 半开区间。拒赔（REJECTED）案件不计入历史赔案。读模型最终一致：投影有延迟，刚创建的赔案可能未可见。
     * </p>
     *
     * @param vin 车辆识别代号（与 subjectId 至少提供一个）
     * @param subjectId 标的ID（保单内唯一，VIN 缺失时的兜底路径）
     * @param incidentFrom 出险日期起（含）
     * @param incidentTo 出险日期止（不含）
     * @return 区间内命中的历史赔案列表
     */
    @GetMapping("/subject-history")
    @Reserved("m27-04: 跨域零消费者（product 改本地赔案投影）；登记见 docs/技术文档/契约登记表.md")
    ApiResponse<List<ClaimResponse>> getClaimsBySubjectHistory(
            @RequestParam(required = false) String vin,
            @RequestParam(required = false) String subjectId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime incidentFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime incidentTo);

    /**
     * 根据状态查询理赔案件列表
     *
     * @param status 状态码
     * @return 理赔案件列表
     */
    @GetMapping("/status/{status}")
    @Reserved("g16-04: 跨域零消费者（管理面经 web 层）；登记见 docs/技术文档/契约登记表.md")
    ApiResponse<List<ClaimResponse>> getClaimsByStatus(@PathVariable String status);

    /**
     * 查询所有理赔案件列表
     *
     * @return 理赔案件列表
     */
    @GetMapping
    @Reserved("g16-04: 跨域零消费者（管理面经 web 层）；登记见 docs/技术文档/契约登记表.md")
    ApiResponse<List<ClaimResponse>> getAllClaims();

    /**
     * 提交查勘（推进理赔阶段至 SURVEY）
     *
     * @param claimId 理赔案件ID
     * @param requestDTO 查勘请求 DTO
     * @return 空响应
     */
    @PostMapping("/{claimId}/survey")
    @Reserved("g16-04: 跨域零消费者（管理面经 web 层）；登记见 docs/技术文档/契约登记表.md")
    ApiResponse<Void> submitSurvey(@PathVariable String claimId,
                                   @RequestBody @Valid SubmitSurveyRequest requestDTO);

    /**
     * 提交定损（推进理赔阶段至 LOSS_ASSESS）
     *
     * @param claimId 理赔案件ID
     * @param requestDTO 定损请求 DTO
     * @return 空响应
     */
    @PostMapping("/{claimId}/loss-assessment")
    @Reserved("g16-04: 跨域零消费者（管理面经 web 层）；登记见 docs/技术文档/契约登记表.md")
    ApiResponse<Void> submitLossAssessment(@PathVariable String claimId,
                                           @RequestBody @Valid SubmitLossAssessmentRequest requestDTO);

    /**
     * 核赔结算（APPROVED → PAID）
     *
     * @param claimId 理赔案件ID
     * @param requestDTO 结算请求 DTO
     * @return 空响应
     */
    @PostMapping("/{claimId}/settlement")
    @Reserved("g16-04: 跨域零消费者（管理面经 web 层）；登记见 docs/技术文档/契约登记表.md")
    ApiResponse<Void> settleClaim(@PathVariable String claimId,
                                  @RequestBody @Valid SettleClaimRequest requestDTO);

    /**
     * 身故给付结算（寿险专属，APPROVED → PAID，按受益人份额一次性给付）
     * <p>
     * 给付总额由下游按保单基本保额精算（CLAIM-2），入参只承载保单定位键与受益人份额规格，
     * 禁止调用方透传金额。
     * </p>
     *
     * @param claimId 理赔案件ID
     * @param requestDTO 身故给付结算请求
     * @return 空响应
     */
    @PostMapping("/{claimId}/death-benefit")
    @Reserved("g16-04: 跨域零消费者（管理面经 web 层）；登记见 docs/技术文档/契约登记表.md")
    ApiResponse<Void> settleDeathBenefit(@PathVariable String claimId,
                                         @RequestBody @Valid SettleDeathBenefitRequest requestDTO);

    /**
     * 全残给付结算（寿险/意外险专属，CLAIM-6，APPROVED → PAID，按受益人份额一次性给付）
     * <p>
     * 给付总额由下游按保单条款精算（基本保额、或账户价值与基本保额孰高），入参只承载保单定位键、
     * 全残证据与受益人份额规格，禁止调用方透传金额。
     * </p>
     *
     * @param claimId 理赔案件ID
     * @param requestDTO 全残给付结算请求
     * @return 空响应
     */
    @PostMapping("/{claimId}/disability-benefit")
    @Reserved("g16-04: 跨域零消费者（管理面经 web 层）；登记见 docs/技术文档/契约登记表.md")
    ApiResponse<Void> settleDisabilityBenefit(@PathVariable String claimId,
                                              @RequestBody @Valid SettleDisabilityBenefitRequest requestDTO);

    /**
     * 打标警示标记（手动打标/规则引擎回写：反欺诈警示 + 统计口径标记）
     * <p>
     * 类型经 {@code AlertType} code 承载（落库枚举化，红线 20），聚合根按类型合并去重（幂等），
     * 投影至读模型 {@code alert_flags} 列供快赔通道判据「无欺诈警示标记」使用。
     * </p>
     *
     * @param claimId 理赔案件ID
     * @param requestDTO 警示标记请求
     * @return 空响应
     */
    @PostMapping("/{claimId}/alert-flags")
    @Reserved("g16-04: 跨域零消费者（管理面经 web 层）；登记见 docs/技术文档/契约登记表.md")
    ApiResponse<Void> flagClaimAlert(@PathVariable String claimId,
                                     @RequestBody @Valid FlagClaimAlertRequest requestDTO);

    /**
     * 快赔自动核赔（小额快赔通道，产品文档 §2.10：规则匹配 + 金额/状态/无欺诈警示判据
     * 全过自动核赔（APPROVED → 结算）并打快赔统计标记）
     *
     * @param claimId 理赔案件ID
     * @return 空响应
     */
    @PostMapping("/{claimId}/quick-pay")
    @Reserved("g16-04: 跨域零消费者（管理面经 web 层）；登记见 docs/技术文档/契约登记表.md")
    ApiResponse<Void> quickPay(@PathVariable String claimId);

    /**
     * 报销理算结算（健康险/宠物险，案件须已核赔通过 APPROVED）
     * <p>
     * 按赔付规则（免赔额/比例/单次限额）与医院网络台账精算给付金额后结算，<b>不接受调用方透传金额</b>。
     * 与 {@link #settleClaim} 的分工：那条由调用方指定金额（或交聚合按定损核定额裁决），本条由系统算。
     * </p>
     *
     * @param claimId 理赔案件ID
     * @param requestDTO 报销理算结算请求（理算四要素 + 给付方式/收款账户/结论）
     * @return 空响应
     */
    @PostMapping("/{claimId}/reimbursement-settlement")
    @Reserved("g16-04: 跨域零消费者（管理面经 web 层）；登记见 docs/技术文档/契约登记表.md")
    ApiResponse<Void> settleReimbursement(@PathVariable String claimId,
                                          @RequestBody @Valid SettleReimbursementRequest requestDTO);

    /**
     * 拒赔（核赔否决，PENDING/PROCESSING → REJECTED）
     *
     * @param claimId 理赔案件ID
     * @param requestDTO 拒赔请求（原因按枚举 code 承载）
     * @return 空响应
     */
    @PostMapping("/{claimId}/reject")
    @Reserved("g16-04: 跨域零消费者（管理面经 web 层）；登记见 docs/技术文档/契约登记表.md")
    ApiResponse<Void> rejectClaim(@PathVariable String claimId,
                                  @RequestBody @Valid RejectClaimRequest requestDTO);

    /**
     * 结案（归档，PAID/REJECTED → CLOSED）
     *
     * @param claimId 理赔案件ID
     * @return 空响应
     */
    @PostMapping("/{claimId}/close")
    @Reserved("g16-04: 跨域零消费者（管理面经 web 层）；登记见 docs/技术文档/契约登记表.md")
    ApiResponse<Void> closeClaim(@PathVariable String claimId);
}
