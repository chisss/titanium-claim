package com.titanium.claim.application.model.config;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * 赔付规则配置入参（application 层配置子域写模型）
 * <p>
 * 新增/更新合一：{@code ruleId} 为空表示新增（application 雪花生成），非空表示全量更新。
 * </p>
 */
@Data
public class ClaimPayoutRuleConfigRequest {
    /** 规则ID（空=新增） */
    private String              ruleId;
    /** 险种线 code（metadata InsuranceProductType） */
    private String              insuranceLine;
    /** 理赔类型 code（metadata ClaimEnum.ClaimType） */
    private String              claimType;
    /** 免赔额（元） */
    private BigDecimal          deductible;
    /** 赔付比例（0-100 百分比） */
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
