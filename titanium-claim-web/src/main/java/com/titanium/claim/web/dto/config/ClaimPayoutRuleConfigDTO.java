package com.titanium.claim.web.dto.config;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 赔付规则配置 DTO（web 前端入参，理赔配置中心）
 * <p>
 * 新增/更新合一：{@code ruleId} 空=新增，非空=全量更新。经 {@code ClaimConfigWebMapper}
 * 翻译为应用层配置写模型。
 * </p>
 */
@Data
public class ClaimPayoutRuleConfigDTO {
    /** 规则ID（空=新增） */
    private String              ruleId;
    /** 险种线 code */
    @NotBlank(message = "险种线不能为空")
    private String              insuranceLine;
    /** 理赔类型 code */
    @NotBlank(message = "理赔类型不能为空")
    private String              claimType;
    /** 免赔额（元） */
    private BigDecimal          deductible;
    /** 赔付比例（0-100 百分比） */
    @NotNull(message = "赔付比例不能为空")
    @Min(value = 0, message = "赔付比例不能小于 0")
    @Max(value = 100, message = "赔付比例不能大于 100")
    private Integer             payoutRatio;
    /** 单次限额（元，空=不限） */
    private BigDecimal          perClaimLimit;
    /** 年度限额（元，空=不限） */
    private BigDecimal          annualLimit;
    /** 医院分档赔付比例（宠物险：档位→0-100） */
    private Map<String, Integer> hospitalTierRatios;
    /** 责任免除清单 */
    private List<String>        exclusions;
}
