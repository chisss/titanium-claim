package com.titanium.claim.web.response.config;

import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * 流程模板配置 VO（web 前端出参，理赔配置中心）
 * <p>
 * 由 {@code ClaimConfigWebMapper} 自 {@code ClaimFlowTemplate} 聚合组装（MapStruct）。
 * </p>
 */
@Data
public class ClaimFlowTemplateConfigVO {
    /** 模板ID */
    private String              templateId;
    /** 险种线 code */
    private String              insuranceLine;
    /** 案件类型 code */
    private String              claimType;
    /** 环节序列（有序） */
    private List<String>        stageSequence;
    /** 各环节时限（小时） */
    private Map<String, Integer> stageTimeLimitHours;
    /** 责任角色 */
    private String              responsibleRole;
    /** 必经校验点 */
    private List<String>        mandatoryCheckpoints;
}
