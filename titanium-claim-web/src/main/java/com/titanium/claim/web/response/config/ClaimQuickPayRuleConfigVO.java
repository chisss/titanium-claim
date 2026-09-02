package com.titanium.claim.web.response.config;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 快赔规则配置 VO（web 前端出参，理赔配置中心）
 * <p>
 * 由 {@code ClaimConfigWebMapper} 自 {@code ClaimQuickPayRule} 聚合组装（MapStruct）。
 * </p>
 */
@Data
public class ClaimQuickPayRuleConfigVO {
    /** 规则ID */
    private String     ruleId;
    /** 适用理赔类型 code */
    private String     claimType;
    /** 通道开关 */
    private boolean    enabled;
    /** 快赔金额阈值（元） */
    private BigDecimal amountThreshold;
}
