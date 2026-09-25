package com.titanium.claim.web.controller.config;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.claim.application.command.config.PayoutRuleConfigService;
import com.titanium.claim.application.command.config.QuickPayRuleConfigService;
import com.titanium.claim.application.query.config.PayoutRuleConfigQueryService;
import com.titanium.claim.application.query.config.QuickPayRuleConfigQueryService;
import com.titanium.claim.web.dto.config.ClaimPayoutRuleConfigDTO;
import com.titanium.claim.web.dto.config.ClaimQuickPayRuleConfigDTO;
import com.titanium.claim.web.mapper.ClaimConfigWebMapper;
import com.titanium.claim.web.response.config.ClaimPayoutRuleConfigVO;
import com.titanium.claim.web.response.config.ClaimQuickPayRuleConfigVO;
import com.titanium.metadata.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 理赔赔付／快赔规则配置控制器（管理后台 HTTP 入口）
 * <p>
 * 路径 {@code /web/v1/config}（与拆分前逐字一致）。覆盖赔付规则、快赔规则两个配置主题。
 * Controller 零业务逻辑。
 * </p>
 */

@RestController
@RequiredArgsConstructor
@RequestMapping("/web/v1/config")
public class ClaimRuleConfigController {

    private final PayoutRuleConfigService          payoutRuleConfigService;
    private final QuickPayRuleConfigService         quickPayRuleConfigService;
    private final PayoutRuleConfigQueryService     payoutRuleConfigQueryService;
    private final QuickPayRuleConfigQueryService    quickPayRuleConfigQueryService;
    private final ClaimConfigWebMapper             claimConfigWebMapper;

    // ==================== 赔付规则配置 ====================

    /** 新增/更新赔付规则（ruleId 空=新增） */
    @PostMapping("/payout-rules")
    public ResponseEntity<ApiResponse<String>> savePayoutRule(@RequestBody @Valid ClaimPayoutRuleConfigDTO dto) {
        // 保留 201：创建语义属 HTTP 传输层，不并入 ApiResponse.code
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(payoutRuleConfigService.savePayoutRule(
                claimConfigWebMapper.toRequest(dto))));
    }

    /** 赔付规则列表（当前租户） */
    @GetMapping("/payout-rules")
    public ApiResponse<List<ClaimPayoutRuleConfigVO>> listPayoutRules() {
        return ApiResponse.success(payoutRuleConfigQueryService.listPayoutRules()
             .stream().map(claimConfigWebMapper::toVO).toList());
    }

    /** 赔付规则详情 */
    @GetMapping("/payout-rules/{ruleId}")
    public ApiResponse<ClaimPayoutRuleConfigVO> getPayoutRule(@PathVariable String ruleId) {
        return ApiResponse.success(claimConfigWebMapper.toVO(
             payoutRuleConfigQueryService.getPayoutRule(ruleId)));
    }

    /** 删除赔付规则（逻辑删除） */
    @DeleteMapping("/payout-rules/{ruleId}")
    public ApiResponse<Void> deletePayoutRule(@PathVariable String ruleId) {
        payoutRuleConfigService.deletePayoutRule(ruleId);
        return ApiResponse.success();
    }

    // ==================== 快赔规则配置 ====================

    /** 新增/更新快赔规则（ruleId 空=新增） */
    @PostMapping("/quick-pay-rules")
    public ResponseEntity<ApiResponse<String>> saveQuickPayRule(@RequestBody @Valid ClaimQuickPayRuleConfigDTO dto) {
        // 保留 201：创建语义属 HTTP 传输层，不并入 ApiResponse.code
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(quickPayRuleConfigService.saveQuickPayRule(
                claimConfigWebMapper.toRequest(dto))));
    }

    /** 快赔规则列表（当前租户） */
    @GetMapping("/quick-pay-rules")
    public ApiResponse<List<ClaimQuickPayRuleConfigVO>> listQuickPayRules() {
        return ApiResponse.success(quickPayRuleConfigQueryService.listQuickPayRules()
             .stream().map(claimConfigWebMapper::toVO).toList());
    }

    /** 快赔规则详情 */
    @GetMapping("/quick-pay-rules/{ruleId}")
    public ApiResponse<ClaimQuickPayRuleConfigVO> getQuickPayRule(@PathVariable String ruleId) {
        return ApiResponse.success(claimConfigWebMapper.toVO(
             quickPayRuleConfigQueryService.getQuickPayRule(ruleId)));
    }

    /** 删除快赔规则（逻辑删除） */
    @DeleteMapping("/quick-pay-rules/{ruleId}")
    public ApiResponse<Void> deleteQuickPayRule(@PathVariable String ruleId) {
        quickPayRuleConfigService.deleteQuickPayRule(ruleId);
        return ApiResponse.success();
    }
}
