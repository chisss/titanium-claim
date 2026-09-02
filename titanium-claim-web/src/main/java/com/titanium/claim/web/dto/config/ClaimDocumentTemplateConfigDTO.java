package com.titanium.claim.web.dto.config;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 单证模板配置 DTO（web 前端入参，理赔配置中心）
 * <p>
 * 新增/更新合一：{@code templateId} 空=新增，非空=全量更新。经 {@code ClaimConfigWebMapper}
 * 翻译为应用层配置写模型。
 * </p>
 */
@Data
public class ClaimDocumentTemplateConfigDTO {
    /** 模板ID（空=新增） */
    private String       templateId;
    /** 险种线 code */
    @NotBlank(message = "险种线不能为空")
    private String       insuranceLine;
    /** 理赔类型 code */
    @NotBlank(message = "理赔类型不能为空")
    private String       claimType;
    /** 必填材料清单 */
    private List<String> requiredDocuments;
    /** 选填材料清单 */
    private List<String> optionalDocuments;
}
