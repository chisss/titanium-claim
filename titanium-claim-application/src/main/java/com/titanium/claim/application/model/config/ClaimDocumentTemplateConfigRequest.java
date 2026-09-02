package com.titanium.claim.application.model.config;

import java.util.List;

import lombok.Data;

/**
 * 单证模板配置入参（application 层配置子域写模型）
 * <p>
 * 新增/更新合一：{@code templateId} 为空表示新增（application 雪花生成），非空表示全量更新。
 * </p>
 */
@Data
public class ClaimDocumentTemplateConfigRequest {
    /** 模板ID（空=新增） */
    private String       templateId;
    /** 险种线 code（metadata InsuranceProductType） */
    private String       insuranceLine;
    /** 理赔类型 code（metadata ClaimEnum.ClaimType） */
    private String       claimType;
    /** 必填材料清单 */
    private List<String> requiredDocuments;
    /** 选填材料清单 */
    private List<String> optionalDocuments;
}
