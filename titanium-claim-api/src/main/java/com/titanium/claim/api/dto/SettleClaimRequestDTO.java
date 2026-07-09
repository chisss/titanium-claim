package com.titanium.claim.api.dto;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 核赔结算请求DTO（理赔核赔阶段，APPROVED → PAID）
 */
@Data
public class SettleClaimRequestDTO {
    /** 核定赔付金额 */
    private BigDecimal settledAmount;
    /** 给付方式：BANK_TRANSFER/CASH/CHECK/OFFSET_PREMIUM */
    private String     payoutMethod;
    /** 收款账户 */
    private String     payeeAccount;
    /** 核赔结论 */
    private String     conclusion;
}
