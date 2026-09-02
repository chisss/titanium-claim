package com.titanium.claim.application.model.config;

import lombok.Data;

/**
 * 时限规则配置入参（application 层配置子域写模型）
 * <p>
 * 新增/更新合一：{@code ruleId} 为空表示新增（application 雪花生成），非空表示全量更新。
 * </p>
 */
@Data
public class ClaimTimeLimitRuleConfigRequest {
    /** 规则ID（空=新增） */
    private String  ruleId;
    /** 险种线 code（metadata InsuranceProductType） */
    private String  insuranceLine;
    /** 案件环节 code（对齐流程模板环节序列） */
    private String  claimStage;
    /** 环节处理时限（小时） */
    private Integer limitHours;
    /** 预警时限（小时，0=不预警） */
    private Integer alertHours;
}
