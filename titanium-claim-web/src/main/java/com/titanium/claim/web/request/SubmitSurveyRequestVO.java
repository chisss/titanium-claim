package com.titanium.claim.web.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 提交查勘请求VO（理赔查勘阶段，后台/端上入口）
 * <p>
 * 面向人机终端接收查勘提交参数，经 {@code ClaimWebMapper} 翻译为应用层查勘入参。
 * </p>
 */
@Data
public class SubmitSurveyRequestVO {
    /** 查勘员ID */
    @NotBlank(message = "查勘员ID不能为空")
    private String       surveyorId;
    /** 查勘报告 */
    private String       surveyReport;
    /** 现场照片URL列表 */
    private List<String> photos;
    /** 查勘结论 */
    private String       conclusion;
}
