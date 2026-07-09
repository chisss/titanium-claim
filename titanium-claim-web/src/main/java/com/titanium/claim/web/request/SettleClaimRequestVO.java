package com.titanium.claim.web.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 核赔结算请求VO（理赔核赔阶段 APPROVED → PAID，后台/端上入口）
 * <p>
 * 面向人机终端接收核赔结算参数，经 {@code ClaimWebMapper} 翻译为应用层结算入参。
 * </p>
 */
@Data
public class SettleClaimRequestVO {
    /** 核定赔付金额 */
    @NotNull(message = "核定赔付金额不能为空")
    private BigDecimal settledAmount;
    /** 给付方式：BANK_TRANSFER/CASH/CHECK/OFFSET_PREMIUM */
    @NotBlank(message = "给付方式不能为空")
    private String     payoutMethod;
    /** 收款账户 */
    private String     payeeAccount;
    /** 核赔结论 */
    private String     conclusion;
}
