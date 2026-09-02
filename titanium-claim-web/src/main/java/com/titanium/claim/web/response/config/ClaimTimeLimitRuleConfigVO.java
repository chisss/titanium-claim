package com.titanium.claim.web.response.config;

import lombok.Data;

/**
 * 时限规则配置 VO（web 前端出参，理赔配置中心）
 * <p>
 * 由 {@code ClaimConfigWebMapper} 自 {@code ClaimTimeLimitRule} 聚合组装（MapStruct）。
 * </p>
 */
@Data
public class ClaimTimeLimitRuleConfigVO {
    /** 规则ID */
    private String  ruleId;
    /** 险种线 code */
    private String  insuranceLine;
    /** 案件环节 code */
    private String  claimStage;
    /** 环节处理时限（小时） */
    private Integer limitHours;
    /** 预警时限（小时，0=不预警） */
    private Integer alertHours;
}
