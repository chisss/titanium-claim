package com.titanium.claim.port.policy;

/**
 * 保单条款引用（PolicyServicePort 出参，领域视角的条款定位摘要）
 * <p>
 * 责任校验（CLAIM-4）的条款定位键：报案只携带保单ID，条款经 policy 域保单条款主数据
 * （PolicyClauseView）取得，再凭 {@code clauseId} 穿透 clause 域取保险责任。
 * </p>
 *
 * @param clauseId   条款ID（指向 clause 域，取责任的定位键）
 * @param clauseCode 条款编码
 * @param clauseName 条款名称
 * @param mainClause 是否主条款（责任校验优先主条款）
 */
public record ClauseRef(
        String clauseId,
        String clauseCode,
        String clauseName,
        Boolean mainClause
) {
}
