package com.titanium.claim.application.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/**
 * 身故给付结算请求（application 写用例入参，寿险身故理赔 APPROVED → PAID）
 * <p>
 * 承载身故证据材料与受益人份额核算，应用层据此装配领域值对象 {@code DeathClaimEvidence} 与
 * {@code BenefitCalculation}，构造 {@code SettleDeathBenefitCommand}。区别于通用核赔结算：身故给付
 * 按受益人份额一次性给付，给付后触发保单终止。
 * </p>
 */
@Data
public class SettleDeathBenefitRequest {

    /** 死亡证明编号 */
    private String deathCertificateNo;
    /** 身故日期 */
    private LocalDateTime deathDate;
    /** 身故原因 */
    private String deathCause;
    /** 是否已办理户籍注销 */
    private boolean householdCancelled;
    /** 受益人关系证明编号 */
    private String beneficiaryProofNo;

    /** 身故给付总额（须等于各受益人份额之和） */
    private BigDecimal totalBenefit;
    /** 给付方式：BANK_TRANSFER/CASH/CHECK/OFFSET_PREMIUM */
    private String payoutMethod;
    /** 核赔意见 */
    private String conclusion;

    /** 受益人份额明细 */
    private List<BeneficiaryShare> shares;

    /**
     * 受益人份额明细
     */
    @Data
    public static class BeneficiaryShare {
        /** 受益人ID */
        private String beneficiaryId;
        /** 受益人姓名 */
        private String beneficiaryName;
        /** 受益比例（0-1） */
        private BigDecimal benefitRatio;
        /** 应得给付额（= 给付总额 × 受益比例） */
        private BigDecimal amount;
    }
}
