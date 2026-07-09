package com.titanium.claim.web.request;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 提交定损请求VO（理赔定损阶段，后台/端上入口）
 * <p>
 * 面向人机终端接收定损提交参数，经 {@code ClaimWebMapper} 翻译为应用层定损入参。
 * </p>
 */
@Data
public class SubmitLossAssessmentRequestVO {
    /** 定损总金额 */
    @NotNull(message = "定损总金额不能为空")
    private BigDecimal        assessedAmount;
    /** 责任比例（0-1，如全责1.0、同责0.5） */
    private BigDecimal        liabilityRatio;
    /** 定损员ID */
    @NotBlank(message = "定损员ID不能为空")
    private String            assessorId;
    /** 定损明细项 */
    private List<LossItemVO> items;

    /** 定损明细项 */
    @Data
    public static class LossItemVO {
        /** 项目名称（如保险杠/挡风玻璃） */
        private String     itemName;
        /** 损失金额 */
        private BigDecimal amount;
    }
}
