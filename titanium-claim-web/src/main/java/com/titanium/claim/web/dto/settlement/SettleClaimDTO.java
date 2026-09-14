package com.titanium.claim.web.dto.settlement;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 核赔结算 DTO（web 前端入参，理赔核赔阶段 APPROVED → PAID）
 * <p>
 * 面向人机终端接收核赔结算参数，经 {@code ClaimWebMapper} 翻译为应用层结算入参。
 * </p>
 */
@Data
public class SettleClaimDTO {
    /**
     * 核定赔付金额
     * <p>
     * 🔴 已定损案件可留空——金额取定损核定额（=（定损总金额−残值）×责任比例）；若填写则必须与定损核定额相等，
     * 人工透传改数将被拒绝。未定损案件必填。
     * </p>
     */
    private BigDecimal settledAmount;
    /** 给付方式：BANK_TRANSFER/CASH/CHECK/OFFSET_PREMIUM */
    @NotBlank(message = "给付方式不能为空")
    private String     payoutMethod;
    /** 收款账户 */
    private String     payeeAccount;
    /** 核赔结论 */
    private String     conclusion;
}
