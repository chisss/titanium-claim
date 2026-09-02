package com.titanium.claim.web.response.config;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * 赔付规则配置 VO（web 前端出参，理赔配置中心）
 * <p>
 * 由 {@code ClaimConfigWebMapper} 自 {@code ClaimPayoutRule} 聚合组装（MapStruct）。
 * </p>
 */
@Data
public class ClaimPayoutRuleConfigVO {
    /** 规则ID */
    private String              ruleId;
    /** 险种线 code */
    private String              insuranceLine;
    /** 理赔类型 code */
    private String              claimType;
    /** 免赔额（元） */
    private BigDecimal          deductible;
    /** 赔付比例（0-100 百分比） */
    private Integer             payoutRatio;
    /** 单次限额（元，空=不限） */
    private BigDecimal          perClaimLimit;
    /** 年度限额（元，空=不限） */
    private BigDecimal          annualLimit;
    /** 医院分档赔付比例（档位→0-100） */
    private Map<String, Integer> hospitalTierRatios;
    /** 责任免除清单 */
    private List<String>        exclusions;
}
