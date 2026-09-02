package com.titanium.claim.api.request;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/**
 * 身故给付结算请求（对外契约，Feign 入参，寿险身故理赔 APPROVED → PAID）
 * <p>
 * 给付总额由下游按保单基本保额精算（CLAIM-2），本契约只承载保单定位键、身故证据与受益人
 * 份额规格，<b>不承载金额</b>（禁止调用方透传金额）。
 * </p>
 */
@Data
public class SettleDeathBenefitRequest {

    /** 保单ID（精算取基本保额的定位键） */
    private String            policyId;
    /** 死亡证明编号 */
    private String            deathCertificateNo;
    /** 身故日期 */
    private LocalDateTime     deathDate;
    /** 身故原因 */
    private String            deathCause;
    /** 是否已办理户籍注销 */
    private boolean           householdCancelled;
    /** 受益人关系证明编号 */
    private String            beneficiaryProofNo;
    /** 给付方式：BANK_TRANSFER/CASH/CHECK/OFFSET_PREMIUM */
    private String            payoutMethod;
    /** 核赔意见 */
    private String            conclusion;
    /** 受益人份额规格列表（应得金额由系统按比例精算） */
    private List<BeneficiaryShare> shares;

    /**
     * 受益人份额规格
     */
    @Data
    public static class BeneficiaryShare {
        /** 受益人ID */
        private String     beneficiaryId;
        /** 受益人姓名 */
        private String     beneficiaryName;
        /** 受益比例（0-1，各受益人比例之和为1） */
        private BigDecimal benefitRatio;
    }
}
