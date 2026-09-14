package com.titanium.claim.api.request.settlement;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 报销理算结算请求（对外契约，Feign 入参，健康险/宠物险）
 * <p>
 * 对已核赔通过（APPROVED）的报销类案件发起结算：给付金额由理赔域按赔付规则配置
 * （免赔额/比例/单次限额）与医院网络台账精算，<b>不接受调用方透传金额</b>——
 * 与 {@link SettleClaimRequest} 的差异正在于此：那条契约要人填金额，本条约定了「系统算」。
 * </p>
 */
@Data
public class SettleReimbursementRequest {
    /** 险种线 code（MEDICAL/PET），与理赔类型共同定位赔付规则 */
    private String     insuranceLine;
    /** 理赔类型 code（MEDICAL_REIMBURSE/PET_MEDICAL） */
    private String     claimType;
    /** 出险医院名称（空=非定点医院，按非定点档位比例理算） */
    private String     hospitalName;
    /** 合规费用（核赔核定后的可赔费用，须大于 0） */
    private BigDecimal eligibleExpense;
    /** 给付方式：BANK_TRANSFER/CASH/CHECK/OFFSET_PREMIUM */
    private String     payoutMethod;
    /** 收款账户 */
    private String     payeeAccount;
    /** 核赔结论 */
    private String     conclusion;
}
