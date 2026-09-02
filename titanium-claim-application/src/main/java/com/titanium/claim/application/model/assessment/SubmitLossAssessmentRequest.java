package com.titanium.claim.application.model.assessment;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

/**
 * 提交定损请求（application 写用例入参）
 */
@Data
public class SubmitLossAssessmentRequest {
    /** 定损总金额 */
    private BigDecimal      assessedAmount;
    /** 残值扣减（损余件残值，可为空=0） */
    private BigDecimal      salvageValue;
    /** 责任比例（0-1） */
    private BigDecimal      liabilityRatio;
    /** 定损员ID */
    private String          assessorId;
    /** 定损明细项 */
    private List<LossItem>  items;

    /** 定损明细项 */
    @Data
    public static class LossItem {
        /** 项目名称 */
        private String     itemName;
        /** 损失金额 */
        private BigDecimal amount;
    }
}
