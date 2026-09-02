package com.titanium.claim.web.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 全残给付结算 DTO（web 前端入参，对应寿险/意外险全残理赔 APPROVED → PAID，CLAIM-6）
 * <p>
 * 承载残疾鉴定材料、受益人份额规格（不含金额），经 {@code ClaimWebMapper#toDisabilityBenefitRequest}
 * 转换为应用层 {@code SettleDisabilityBenefitRequest}，由 {@code ClaimSettlementOrchestrator} 取保单
 * 基本保额/账户价值精算给付金额并装配领域命令（给付总额由系统按条款计算，禁止调用方透传金额）。
 * </p>
 */
@Data
public class SettleDisabilityBenefitDTO {

    /** 保单ID（精算取基本保额/账户价值的定位键） */
    @NotBlank(message = "保单ID不能为空")
    private String policyId;

    /** 残疾鉴定证明编号 */
    @NotBlank(message = "残疾鉴定证明编号不能为空")
    private String disabilityCertificateNo;

    /** 残疾等级（如「一级伤残」） */
    @NotBlank(message = "残疾等级不能为空")
    private String disabilityGrade;

    /** 鉴定日期 */
    private LocalDateTime assessmentDate;

    /** 鉴定机构名称 */
    private String assessmentAgency;

    /** 受益人关系证明编号 */
    private String beneficiaryProofNo;

    /** 给付方式：BANK_TRANSFER/CASH/CHECK/OFFSET_PREMIUM */
    @NotBlank(message = "给付方式不能为空")
    private String payoutMethod;

    /** 核赔意见 */
    private String conclusion;

    /** 受益人份额规格列表（应得金额由系统按比例精算，禁止调用方传金额） */
    @Valid
    private List<BeneficiaryShare> shares;

    /**
     * 受益人份额规格
     */
    @Data
    public static class BeneficiaryShare {

        /** 受益人ID */
        @NotBlank(message = "受益人ID不能为空")
        private String beneficiaryId;

        /** 受益人姓名 */
        @NotBlank(message = "受益人姓名不能为空")
        private String beneficiaryName;

        /** 受益比例（0-1，各受益人比例之和为1） */
        @NotNull(message = "受益比例不能为空")
        private BigDecimal benefitRatio;
    }
}
