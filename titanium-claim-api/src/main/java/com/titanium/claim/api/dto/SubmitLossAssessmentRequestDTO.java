package com.titanium.claim.api.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

/**
 * 提交定损请求DTO（理赔定损阶段）
 */
@Data
public class SubmitLossAssessmentRequestDTO {
    /** 定损总金额 */
    private BigDecimal    assessedAmount;
    /** 责任比例（0-1，如全责1.0、同责0.5） */
    private BigDecimal    liabilityRatio;
    /** 定损员ID */
    private String        assessorId;
    /** 定损明细项 */
    private List<LossItemDTO> items;

    /** 定损明细项 */
    @Data
    public static class LossItemDTO {
        /** 项目名称（如保险杠/挡风玻璃） */
        private String     itemName;
        /** 损失金额 */
        private BigDecimal amount;
    }
}
