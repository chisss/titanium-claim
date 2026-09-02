package com.titanium.claim.application.model.config;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 快赔规则配置入参（application 层配置子域写模型）
 * <p>
 * 新增/更新合一：{@code ruleId} 为空表示新增（application 雪花生成），非空表示全量更新。
 * </p>
 */
@Data
public class ClaimQuickPayRuleConfigRequest {
    /** 规则ID（空=新增） */
    private String     ruleId;
    /** 适用理赔类型 code（metadata ClaimEnum.ClaimType） */
    private String     claimType;
    /** 通道开关（null 视为关闭） */
    private Boolean    enabled;
    /** 快赔金额阈值（元） */
    private BigDecimal amountThreshold;
}
