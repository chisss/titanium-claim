package com.titanium.claim.application.model.assessment;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 报销理算入参（application 读用例入参，健康险/宠物险）
 * <p>
 * 携带理算所需的定位键与事实：险种线 × 理赔类型定位赔付规则，出险医院名（空=非定点）决定结算渠道，
 * 合规费用为核赔核定后的可赔金额。不含比例/限额等规则参数——参数由系统从赔付规则配置取，
 * 金额由领域服务精算，杜绝透传。
 * </p>
 */
@Data
public class ReimbursementSettlementRequest {
    /** 险种线 code（metadata InsuranceProductType，如 MEDICAL/PET） */
    private String       insuranceLine;
    /** 理赔类型 code（metadata ClaimEnum.ClaimType，如 MEDICAL_REIMBURSE/PET_MEDICAL） */
    private String       claimType;
    /** 出险医院名称（空=非定点医院） */
    private String       hospitalName;
    /** 合规费用（核定后的可赔费用） */
    private BigDecimal   eligibleExpense;
}
