package com.titanium.claim.web.dto.settlement;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 报销理算结算 DTO（web 前端入参，健康险/宠物险）
 * <p>
 * 面向人机终端：核赔员对已核赔通过的案件发起「按理算金额结算」。给付金额<b>不由前端传入</b>——
 * 由理赔域按赔付规则（免赔额/比例/限额）与医院网络台账精算，经 {@code ClaimWebMapper}
 * 翻译为应用层结算入参。区别于 {@link SettleClaimDTO}：那条契约由人填金额，本条约定系统算。
 * </p>
 */
@Data
public class SettleReimbursementDTO {
    /** 险种线 code（MEDICAL/PET），与理赔类型共同定位赔付规则 */
    @NotBlank(message = "险种线不能为空")
    private String     insuranceLine;
    /** 理赔类型 code（MEDICAL_REIMBURSE/PET_MEDICAL） */
    @NotBlank(message = "理赔类型不能为空")
    private String     claimType;
    /** 出险医院名称（空=非定点医院，按非定点档位比例理算） */
    private String     hospitalName;
    /** 合规费用（核赔核定后的可赔费用） */
    @NotNull(message = "合规费用不能为空")
    @DecimalMin(value = "0.01", message = "合规费用必须大于0")
    private BigDecimal eligibleExpense;
    /** 给付方式：BANK_TRANSFER/CASH/CHECK/OFFSET_PREMIUM */
    @NotBlank(message = "给付方式不能为空")
    private String     payoutMethod;
    /** 收款账户 */
    private String     payeeAccount;
    /** 核赔结论 */
    private String     conclusion;
}
