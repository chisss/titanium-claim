package com.titanium.claim.web.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 身故给付结算 DTO（web 前端入参，对应寿险身故理赔 APPROVED → PAID）
 * <p>
 * 承载死亡证明材料、受益人份额规格（不含金额），经 {@code ClaimWebMapper#toDeathBenefitRequest} 转换为
 * 应用层 {@code SettleDeathBenefitRequest}，由 {@code ClaimSettlementOrchestrator} 取保单基本保额精算给付
 * 金额并装配领域命令（CLAIM-2：给付总额由系统按条款计算，禁止调用方透传金额）。
 * </p>
 */
@Data
public class SettleDeathBenefitDTO {

    /** 保单ID（精算取基本保额的定位键） */
    @NotBlank(message = "保单ID不能为空")
    private String policyId;

    /** 死亡证明编号 */
    @NotBlank(message = "死亡证明编号不能为空")
    private String deathCertificateNo;

    /** 身故日期 */
    @NotNull(message = "身故日期不能为空")
    private LocalDateTime deathDate;

    /** 身故原因 */
    @NotBlank(message = "身故原因不能为空")
    private String deathCause;

    /** 是否已办理户籍注销 */
    private boolean householdCancelled;

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
