package com.titanium.claim.web.response.assessment;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 报销理算 VO（web 前端出参，核赔理算结果）
 * <p>
 * 由 {@code ClaimWebMapper} 自 {@code ReimbursementAdjustmentResult} 组装（MapStruct），
 * 结算渠道枚举落 code。
 * </p>
 */
@Data
public class ReimbursementAdjustmentVO {
    /** 理算应付金额（元） */
    private BigDecimal payableAmount;
    /** 实际套用的赔付比例（0-100） */
    private Integer     payoutRatio;
    /** 结算渠道 code（DESIGNATED 定点/NON_DESIGNATED 非定点） */
    private String      settlementChannel;
    /** 是否触发单次限额封顶 */
    private Boolean     cappedByLimit;
}
