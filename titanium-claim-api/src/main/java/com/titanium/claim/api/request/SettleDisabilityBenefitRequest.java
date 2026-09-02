package com.titanium.claim.api.request;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/**
 * 全残给付结算请求（对外契约，Feign 入参，寿险/意外险全残理赔 APPROVED → PAID，CLAIM-6）
 * <p>
 * 给付总额由下游按保单条款精算（基本保额、或账户价值与基本保额孰高），本契约只承载保单定位键、
 * 全残证据与受益人份额规格，<b>不承载金额</b>（禁止调用方透传金额）。
 * </p>
 */
@Data
public class SettleDisabilityBenefitRequest {

    /** 保单ID（精算取基本保额/账户价值的定位键） */
    private String            policyId;
    /** 残疾鉴定证明编号 */
    private String            disabilityCertificateNo;
    /** 残疾等级（如「一级伤残」） */
    private String            disabilityGrade;
    /** 鉴定日期 */
    private LocalDateTime     assessmentDate;
    /** 鉴定机构名称 */
    private String            assessmentAgency;
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
