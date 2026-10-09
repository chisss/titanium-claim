package com.titanium.claim.valueobject;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 理赔差异化阈值（g09-03：规则引擎 CLAIM 类型规则集求值结论，G09/AC-05 · AC-06）
 * <p>
 * 承载「可由运营按租户/产品差异化配置」的<b>准入类</b>阈值结论（首批两类：<b>等待期</b>与
 * <b>情景化准入</b>）——金额类参数（赔付比例/单次限额/年度限额）不在此列，它们留在既有
 * {@code ClaimPayoutRule}（见 G09 文档 §五判据：金额参数 vs 准入规则）。
 * </p>
 * <p>
 * 每个维度以 {@code null} 表示「该维度无规则结论」，消费端<b>逐维回落</b>既有链路——
 * 等待期回落条款 {@code waitingPeriodDays()}（{@code ClaimService.isClaimInCoverage}
 * 的既有纯计算路径不变）。
 * </p>
 * <p>
 * 🔴 <b>本批消费面（如实登记）</b>：等待期维度已接入报案责任校验（{@code CoverageValidator}）；
 * 情景化准入维度由适配器映射承载（规则输出存在即装填），消费端待后续任务接入，本批不虚构
 * 无验收判据的行为。
 * </p>
 *
 * @param waitingPeriodDays 差异化等待期天数（替代条款等待期；{@code 0} = 规则明确无等待期约束，
 *                          {@code null} = 无规则结论、回落条款值）
 * @param admissionDecision 情景化准入结论（取值 = 规则引擎 {@code RuleDecision} 枚举 code，
 *                          如 {@code PASS}/{@code REFER}/{@code REJECT}；{@code null} = 无规则结论）
 */
public record ClaimDifferentiatedThreshold(Integer waitingPeriodDays, String admissionDecision) {

    /** 空结论：无生效规则集或规则未输出阈值——消费端全部回落到既有链路 */
    public static final ClaimDifferentiatedThreshold EMPTY = new ClaimDifferentiatedThreshold(null, null);

    /** 是否为空结论（两维度均无规则输出） */
    // 🔴 @JsonIgnore 不可删（D-501-19，与 D-501-18 investment/regulatory 同病）：
    //    派生 getter 非 record 分量，却会被 Axon 的 Jackson 序列化器写进事件/载荷
    //    （isEmpty → 属性 "empty"），重放时报 UnrecognizedPropertyException → 聚合无法加载。
    @JsonIgnore
    public boolean isEmpty() {
        return waitingPeriodDays == null && admissionDecision == null;
    }
}
