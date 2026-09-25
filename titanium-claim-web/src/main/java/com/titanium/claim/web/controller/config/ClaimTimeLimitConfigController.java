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

import com.titanium.claim.application.command.config.TimeLimitRuleConfigService;
import com.titanium.claim.application.query.config.TimeLimitRuleConfigQueryService;
import com.titanium.claim.web.dto.config.ClaimTimeLimitRuleConfigDTO;
import com.titanium.claim.web.mapper.ClaimConfigWebMapper;
import com.titanium.claim.web.response.config.ClaimTimeLimitRuleConfigVO;
import com.titanium.metadata.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 理赔时限规则配置控制器（管理后台 HTTP 入口）
 * <p>
 * 路径 {@code /web/v1/config}（与拆分前逐字一致）。覆盖时限规则单一配置主题。
 * Controller 零业务逻辑。
 * </p>
 */

@RestController
@RequiredArgsConstructor
@RequestMapping("/web/v1/config")
public class ClaimTimeLimitConfigController {

    private final TimeLimitRuleConfigService       timeLimitRuleConfigService;
    private final TimeLimitRuleConfigQueryService  timeLimitRuleConfigQueryService;
    private final ClaimConfigWebMapper             claimConfigWebMapper;

    // ==================== 时限规则配置 ====================

    /** 新增/更新时限规则（ruleId 空=新增） */
    @PostMapping("/time-limit-rules")
    public ResponseEntity<ApiResponse<String>> saveTimeLimitRule(@RequestBody @Valid ClaimTimeLimitRuleConfigDTO dto) {
        // 保留 201：创建语义属 HTTP 传输层，不并入 ApiResponse.code
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(timeLimitRuleConfigService.saveTimeLimitRule(
                claimConfigWebMapper.toRequest(dto))));
    }

    /** 时限规则列表（当前租户） */
    @GetMapping("/time-limit-rules")
    public ApiResponse<List<ClaimTimeLimitRuleConfigVO>> listTimeLimitRules() {
        return ApiResponse.success(timeLimitRuleConfigQueryService.listTimeLimitRules()
             .stream().map(claimConfigWebMapper::toVO).toList());
    }

    /** 时限规则详情 */
    @GetMapping("/time-limit-rules/{ruleId}")
    public ApiResponse<ClaimTimeLimitRuleConfigVO> getTimeLimitRule(@PathVariable String ruleId) {
        return ApiResponse.success(claimConfigWebMapper.toVO(
             timeLimitRuleConfigQueryService.getTimeLimitRule(ruleId)));
    }

    /** 删除时限规则（逻辑删除） */
    @DeleteMapping("/time-limit-rules/{ruleId}")
    public ApiResponse<Void> deleteTimeLimitRule(@PathVariable String ruleId) {
        timeLimitRuleConfigService.deleteTimeLimitRule(ruleId);
        return ApiResponse.success();
    }
}
