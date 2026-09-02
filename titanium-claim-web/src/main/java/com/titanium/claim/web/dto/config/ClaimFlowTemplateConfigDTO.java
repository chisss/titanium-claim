package com.titanium.claim.web.dto.config;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * 流程模板配置 DTO（web 前端入参，理赔配置中心）
 * <p>
 * 新增/更新合一：{@code templateId} 空=新增，非空=全量更新。经 {@code ClaimConfigWebMapper}
 * 翻译为应用层配置写模型。
 * </p>
 */
@Data
public class ClaimFlowTemplateConfigDTO {
    /** 模板ID（空=新增） */
    private String              templateId;
    /** 险种线 code */
    @NotBlank(message = "险种线不能为空")
    private String              insuranceLine;
    /** 案件类型 code */
    @NotBlank(message = "案件类型不能为空")
    private String              claimType;
    /** 环节序列（有序） */
    @NotEmpty(message = "环节序列不能为空")
    private List<String>        stageSequence;
    /** 各环节时限（小时），key 属于环节序列 */
    private Map<String, Integer> stageTimeLimitHours;
    /** 责任角色 */
    private String              responsibleRole;
    /** 必经校验点（环节名列表） */
    private List<String>        mandatoryCheckpoints;
}
