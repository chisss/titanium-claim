package com.titanium.claim.web.response.config;

import java.util.List;

import lombok.Data;

/**
 * 单证模板配置 VO（web 前端出参，理赔配置中心）
 * <p>
 * 由 {@code ClaimConfigWebMapper} 自 {@code ClaimDocumentTemplate} 聚合组装（MapStruct）。
 * </p>
 */
@Data
public class ClaimDocumentTemplateConfigVO {
    /** 模板ID */
    private String       templateId;
    /** 险种线 code */
    private String       insuranceLine;
    /** 理赔类型 code */
    private String       claimType;
    /** 必填材料清单 */
    private List<String> requiredDocuments;
    /** 选填材料清单 */
    private List<String> optionalDocuments;
}
