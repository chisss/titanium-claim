package com.titanium.claim.api.request;

import java.util.List;

import lombok.Data;

/**
 * 理赔警示标记请求（对外契约，Feign 入参：手动打标/规则引擎风险评分回写）
 * <p>
 * 类型经 {@code AlertType} code 承载（落库枚举化，红线 20），命中规则标识可空（人工标记）。
 * </p>
 */
@Data
public class FlagClaimAlertRequest {

    /** 警示标记列表 */
    private List<FlagItem> flags;

    /**
     * 单条警示标记：类型 code + 命中规则标识
     */
    @Data
    public static class FlagItem {

        /** 警示标记类型 code（LATE_REPORT/MULTIPLE_REPORTS/RISK_SCORE/QUICK_PAY） */
        private String typeCode;
        /** 命中规则标识（可空=人工标记） */
        private String ruleCode;
    }
}
