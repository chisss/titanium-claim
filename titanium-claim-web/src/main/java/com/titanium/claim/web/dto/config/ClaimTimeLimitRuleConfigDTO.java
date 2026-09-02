package com.titanium.claim.web.dto.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 时限规则配置 DTO（web 前端入参，理赔配置中心）
 * <p>
 * 新增/更新合一：{@code ruleId} 空=新增，非空=全量更新。经 {@code ClaimConfigWebMapper}
 * 翻译为应用层配置写模型。
 * </p>
 */
@Data
public class ClaimTimeLimitRuleConfigDTO {
    /** 规则ID（空=新增） */
    private String  ruleId;
    /** 险种线 code */
    @NotBlank(message = "险种线不能为空")
    private String  insuranceLine;
    /** 案件环节 code */
    @NotBlank(message = "案件环节不能为空")
    private String  claimStage;
    /** 环节处理时限（小时） */
    @NotNull(message = "环节时限不能为空")
    @Min(value = 1, message = "环节时限必须大于 0")
    private Integer limitHours;
    /** 预警时限（小时，0=不预警） */
    private Integer alertHours;
}
