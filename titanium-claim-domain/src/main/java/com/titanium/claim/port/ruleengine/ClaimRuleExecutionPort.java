package com.titanium.claim.port.ruleengine;

import com.titanium.claim.valueobject.ClaimDifferentiatedThreshold;

/**
 * 理赔差异化阈值规则执行端口（对端域：rule-engine，g09-03 · G09/AC-05 · AC-06）。
 * <p>
 * 报案责任校验前，向规则引擎求「本单适用的差异化阈值」（首批两类：等待期与情景化准入），
 * 使运营可按租户/产品差异化配置（改配置不发版）；既往等待期只能来自条款固定值，
 * 无法按产品差异化。本端口是 {@code RuleSetType.CLAIM} 的域内调用方。
 * </p>
 * <p>
 * 🔴 <b>失败语义（失败关闭，禁降级）</b>：
 * </p>
 * <ul>
 *   <li><b>查得到且无生效规则集</b>（规则集列表无本 code 或未激活）⇒ 返回
 *       {@link ClaimDifferentiatedThreshold#EMPTY}，消费端回落条款值——这是<b>合法的未配置态</b>；</li>
 *   <li><b>规则引擎不可达、应答非成功码、载荷无效</b>（如等待期输出非数值）⇒ 一律<b>抛出</b>
 *       （{@code ClaimErrorCode.CLAIM_RULE_ENGINE_REMOTE_ERROR}，30 段），<b>不得</b>降级为 EMPTY
 *       ——「规则说有」与「问不到规则」若同形，配置的阈值会静默失效而全链无异常可看
 *       （等待期触碰赔付结论，宁可拒绝也不静默用错值）。</li>
 * </ul>
 * <p>
 * 🔴 <b>求值变量集（与规则编写者的契约）</b>：本端口固定供给三个变量，键名见下列常量；
 * 规则表达式引用任何<b>未供给</b>的变量都会在求值期显式失败（SpEL 对未知变量抛错）。
 * </p>
 * <ul>
 *   <li>{@link #FACT_POLICY_ID} —— 保单ID（可选，溯源用；值可为 null）；</li>
 *   <li>{@link #FACT_PRODUCT_ID} —— 产品ID（差异化主维度，如「意外险 0 天 / 重疾险 90 天」；
 *       保单未关联产品时值为 null，规则可按其余维度匹配）；</li>
 *   <li>{@link #FACT_CLAIM_TYPE} —— 理赔类型编码（{@code ClaimEnum.ClaimType} 的 code，如
 *       {@code ACCIDENT}/{@code DEATH}）。</li>
 * </ul>
 * <p>
 * 🔴 <b>规则集与输出契约</b>：规则集编码固定为 {@link #RULE_SET_CODE}（须按
 * {@code RuleSetType.CLAIM} 类型创建并<b>显式激活</b>才生效，默认不生效）；
 * 规则输出经 {@code outputs} 携带，键名见 {@link #OUTPUT_WAITING_PERIOD_DAYS} /
 * {@link #OUTPUT_ADMISSION_DECISION}，可只输出其中一个维度（部分结论合法）。
 * </p>
 */
public interface ClaimRuleExecutionPort {

    /** 规则集编码（规则编写者的契约键；类型须为 CLAIM、状态须为 ACTIVE） */
    String RULE_SET_CODE = "CLAIM_DIFFERENTIATED_THRESHOLD";

    /** 变量名：保单ID（可选，可为 null） */
    String FACT_POLICY_ID = "policyId";

    /** 变量名：产品ID（差异化主维度，可为 null） */
    String FACT_PRODUCT_ID = "productId";

    /** 变量名：理赔类型编码 */
    String FACT_CLAIM_TYPE = "claimType";

    /** 输出键：差异化等待期天数（数值） */
    String OUTPUT_WAITING_PERIOD_DAYS = "waitingPeriodDays";

    /** 输出键：情景化准入结论（{@code RuleDecision} 枚举 code 字符串） */
    String OUTPUT_ADMISSION_DECISION = "admissionDecision";

    /**
     * 求本单适用的差异化阈值。
     *
     * @param request 求值请求（租户 + 保单/产品/理赔类型）
     * @return 差异化阈值结论；无生效规则集时为 {@link ClaimDifferentiatedThreshold#EMPTY}（非 null）
     * @throws com.titanium.metadata.exception.DomainException 规则引擎不可达 / 应答非成功 / 载荷无效
     */
    ClaimDifferentiatedThreshold resolveThreshold(DifferentiatedThresholdRequest request);

    /**
     * 差异化阈值求值请求。
     * <p>
     * 只承载规则匹配所需的决策键与溯源键：日期类上下文<b>不</b>传入——「出险是否在等待期内」的
     * 判定仍是 {@code ClaimService.isClaimInCoverage} 的既有纯计算（规则只决定等待期天数）。
     * </p>
     *
     * @param tenantId  租户ID（审计与规则匹配上下文）
     * @param policyId  保单ID（业务头 X-Business-Id 透传与变量溯源，可为 null）
     * @param productId 产品ID（差异化主维度，可为 null=保单未关联产品）
     * @param claimType 理赔类型编码（{@code ClaimEnum.ClaimType} 的 code）
     */
    record DifferentiatedThresholdRequest(String tenantId, String policyId, String productId, String claimType) {
    }
}
