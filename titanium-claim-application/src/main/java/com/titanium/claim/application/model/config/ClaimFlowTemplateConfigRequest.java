package com.titanium.claim.application.model.config;

import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * 流程模板配置入参（application 层配置子域写模型）
 * <p>
 * 新增/更新合一：{@code templateId} 为空表示新增（application 雪花生成），非空表示全量更新。
 * </p>
 */
@Data
public class ClaimFlowTemplateConfigRequest {
    /** 模板ID（空=新增） */
    private String              templateId;
    /** 险种线 code（metadata InsuranceProductType） */
    private String              insuranceLine;
    /** 案件类型 code（metadata ClaimEnum.ClaimType） */
    private String              claimType;
    /** 环节序列（有序） */
    private List<String>        stageSequence;
    /** 各环节时限（小时），key 属于环节序列 */
    private Map<String, Integer> stageTimeLimitHours;
    /** 责任角色 */
    private String              responsibleRole;
    /** 必经校验点（环节名列表） */
    private List<String>        mandatoryCheckpoints;
}
