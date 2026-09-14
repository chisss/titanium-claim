package com.titanium.claim.api.request.settlement;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 核赔结算请求（对外契约，Feign 入参，理赔核赔阶段 APPROVED → PAID）
 */
@Data
public class SettleClaimRequest {
    /**
     * 核定赔付金额
     * <p>
     * 🔴 已定损案件可留空——金额取定损核定额（=（定损总金额−残值）×责任比例）；若填写则必须与定损核定额相等，
     * 人工透传改数将被拒绝。未定损案件必填。
     * </p>
     */
    private BigDecimal settledAmount;
    /** 给付方式：BANK_TRANSFER/CASH/CHECK/OFFSET_PREMIUM */
    private String     payoutMethod;
    /** 收款账户 */
    private String     payeeAccount;
    /** 核赔结论 */
    private String     conclusion;
}
