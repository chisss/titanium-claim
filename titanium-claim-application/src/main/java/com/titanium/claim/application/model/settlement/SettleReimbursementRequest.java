package com.titanium.claim.application.model.settlement;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 报销理算结算请求（application 写用例入参，健康险/宠物险）
 * <p>
 * 核赔员对报销类案件发起「按理算金额结算」：前半段是理算四要素（险种线 × 理赔类型定位赔付规则、
 * 出险医院名决定结算渠道、合规费用），后半段是给付参数（给付方式/收款账户/结论）。
 * </p>
 * <p>
 * 🔴 <b>金额口径（m16-1905）</b>：赔付金额<b>不由前端传入</b>——由
 * {@code ReimbursementAdjustmentService} 按赔付规则配置精算（免赔额/比例/单次限额），
 * 医院网络台账裁决定点/非定点比例。若案件在案定损，聚合仍以定损核定额为唯一权威：
 * 理算金额与核定额不等即拒（失败关闭，不静默改写金额，也不静默改用核定额）。
 * </p>
 * <p>
 * 本模型不含 {@code claimId}——案件标识是资源定位符，由 REST 路径与编排器形参承载，
 * 试算（{@code ReimbursementSettlementRequest}）与结算共用同一套理算入参，互不污染。
 * </p>
 */
@Data
public class SettleReimbursementRequest {
    /** 险种线 code（metadata InsuranceProductType，如 MEDICAL/PET） */
    private String     insuranceLine;
    /** 理赔类型 code（metadata ClaimEnum.ClaimType，如 MEDICAL_REIMBURSE/PET_MEDICAL） */
    private String     claimType;
    /** 出险医院名称（空=非定点医院） */
    private String     hospitalName;
    /** 合规费用（核定后的可赔费用） */
    private BigDecimal eligibleExpense;
    /** 给付方式：BANK_TRANSFER/CASH/CHECK/OFFSET_PREMIUM */
    private String     payoutMethod;
    /** 收款账户 */
    private String     payeeAccount;
    /** 核赔结论 */
    private String     conclusion;
}
