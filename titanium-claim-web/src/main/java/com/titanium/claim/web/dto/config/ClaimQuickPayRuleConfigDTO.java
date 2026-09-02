package com.titanium.claim.web.dto.config;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 快赔规则配置 DTO（web 前端入参，理赔配置中心）
 * <p>
 * 新增/更新合一：{@code ruleId} 空=新增，非空=全量更新。经 {@code ClaimConfigWebMapper}
 * 翻译为应用层配置写模型。
 * </p>
 */
@Data
public class ClaimQuickPayRuleConfigDTO {
    /** 规则ID（空=新增） */
    private String     ruleId;
    /** 适用理赔类型 code */
    @NotBlank(message = "理赔类型不能为空")
    private String     claimType;
    /** 通道开关 */
    @NotNull(message = "通道开关不能为空")
    private Boolean    enabled;
    /** 快赔金额阈值（元） */
    @NotNull(message = "快赔金额阈值不能为空")
    private BigDecimal amountThreshold;
}
