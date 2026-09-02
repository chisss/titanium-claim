package com.titanium.claim.application.model.assessment;

import java.util.List;

import lombok.Data;

/**
 * 理赔警示标记入参（application 层入参模型，web/api 经 WebMapper 收敛）
 * <p>
 * 手动打标入口（人工复核标记、规则引擎风险评分回写）：类型经 {@code AlertType} code 承载
 * （落库枚举化，红线 20），命中规则标识 {@code ruleCode} 可空（人工标记无规则来源）。
 * </p>
 */
@Data
public class FlagClaimAlertRequest {

    /** 警示标记列表 */
    private List<FlagItem> flags;

    /** 单条警示标记：类型 code + 命中规则标识 */
    @Data
    public static class FlagItem {
        /** 警示标记类型 code（AlertType，如 LATE_REPORT/MULTIPLE_REPORTS/RISK_SCORE/QUICK_PAY） */
        private String typeCode;
        /** 命中规则标识（稳定规则 key，可空=人工标记） */
        private String ruleCode;
    }
}
