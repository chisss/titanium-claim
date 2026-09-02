package com.titanium.claim.web.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * 理赔警示标记 DTO（web 前端入参，手动打标：人工复核标记/规则引擎风险评分回写）
 * <p>
 * 类型经 {@code AlertType} code 承载（落库枚举化，红线 20），命中规则标识可空（人工标记）。
 * 经 {@code ClaimWebMapper#toFlagAlertRequest} 转换为应用层 {@code FlagClaimAlertRequest}，
 * 聚合根按类型合并去重（幂等）后投影至读模型 {@code alert_flags} 列。
 * </p>
 */
@Data
public class FlagClaimAlertDTO {

    /** 警示标记列表 */
    @NotEmpty(message = "警示标记列表不能为空")
    @Valid
    private List<FlagItem> flags;

    /**
     * 单条警示标记：类型 code + 命中规则标识
     */
    @Data
    public static class FlagItem {

        /** 警示标记类型 code（LATE_REPORT/MULTIPLE_REPORTS/RISK_SCORE/QUICK_PAY） */
        @NotBlank(message = "警示标记类型不能为空")
        private String typeCode;

        /** 命中规则标识（可空=人工标记） */
        private String ruleCode;
    }
}
