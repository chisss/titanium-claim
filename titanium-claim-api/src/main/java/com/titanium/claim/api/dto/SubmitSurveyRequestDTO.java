package com.titanium.claim.api.dto;

import java.util.List;

import lombok.Data;

/**
 * 提交查勘请求DTO（理赔查勘阶段）
 */
@Data
public class SubmitSurveyRequestDTO {
    /** 查勘员ID */
    private String       surveyorId;
    /** 查勘报告 */
    private String       surveyReport;
    /** 现场照片URL列表 */
    private List<String> photos;
    /** 查勘结论 */
    private String       conclusion;
}
