package com.titanium.claim.application.model;

import java.util.List;

import lombok.Data;

/**
 * 提交查勘请求（application 写用例入参）
 */
@Data
public class SubmitSurveyRequest {
    /** 查勘员ID */
    private String       surveyorId;
    /** 查勘报告 */
    private String       surveyReport;
    /** 现场照片URL列表 */
    private List<String> photos;
    /** 查勘结论 */
    private String       conclusion;
}
