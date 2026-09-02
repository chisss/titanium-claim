package com.titanium.claim.application.model.settlement;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 核赔结算请求（application 写用例入参）
 */
@Data
public class SettleClaimRequest {
    /** 核定赔付金额 */
    private BigDecimal settledAmount;
    /** 给付方式：BANK_TRANSFER/CASH/CHECK/OFFSET_PREMIUM */
    private String     payoutMethod;
    /** 收款账户 */
    private String     payeeAccount;
    /** 核赔结论 */
    private String     conclusion;
}
