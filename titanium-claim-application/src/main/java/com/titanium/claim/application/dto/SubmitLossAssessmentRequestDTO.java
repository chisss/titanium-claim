package com.titanium.claim.application.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

/**
 * 提交定损请求DTO（应用层命令入参）
 */
@Data
public class SubmitLossAssessmentRequestDTO {
    /** 定损总金额 */
    private BigDecimal        assessedAmount;
    /** 责任比例（0-1） */
    private BigDecimal        liabilityRatio;
    /** 定损员ID */
    private String            assessorId;
    /** 定损明细项 */
    private List<LossItemDTO> items;

    /** 定损明细项 */
    @Data
    public static class LossItemDTO {
        /** 项目名称 */
        private String     itemName;
        /** 损失金额 */
        private BigDecimal amount;
    }
}
