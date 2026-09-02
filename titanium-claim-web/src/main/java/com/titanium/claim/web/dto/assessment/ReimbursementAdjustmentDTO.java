package com.titanium.claim.web.dto.assessment;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 报销理算 DTO（web 前端入参，健康险/宠物险核赔理算）
 * <p>
 * 核赔员核定合规费用后提交理算试算：系统按赔付规则（免赔额/比例/限额）与医院网络台账自动裁决
 * 定点/非定点结算渠道并计算应付金额。经 {@code ClaimWebMapper} 翻译为应用层理算入参。
 * </p>
 */
@Data
public class ReimbursementAdjustmentDTO {
    /** 险种线 code（MEDICAL/PET） */
    @NotBlank(message = "险种线不能为空")
    private String       insuranceLine;
    /** 理赔类型 code（MEDICAL_REIMBURSE/PET_MEDICAL） */
    @NotBlank(message = "理赔类型不能为空")
    private String       claimType;
    /** 出险医院名称（空=非定点医院） */
    private String       hospitalName;
    /** 合规费用（核定后的可赔费用） */
    @NotNull(message = "合规费用不能为空")
    @DecimalMin(value = "0.01", message = "合规费用必须大于0")
    private BigDecimal   eligibleExpense;
}
