package com.titanium.claim.valueobject;

import java.math.BigDecimal;

import com.titanium.claim.aggregate.ClaimHospitalNetwork;
import com.titanium.claim.aggregate.ClaimPayoutRule;
import com.titanium.claim.common.enums.config.SettlementChannel;

/**
 * 报销理算入参值对象（领域服务入参，§3.4.4 入参仅聚合/值对象）
 * <p>
 * 携带理算所需的合规费用与两条配置聚合：赔付规则（免赔额/比例/限额参数源）与出险医院台账
 * （可为空 = 非定点医院）。赔付规则缺失由 application 取数层抛 {@code CLAIM_CONFIG_NOT_FOUND}，
 * 医院台账资格判定（{@code isEligible}）内聚于 {@link ClaimHospitalNetwork} 聚合。
 * </p>
 *
 * @param eligibleExpense 合规费用（核定后的可赔费用）
 * @param payoutRule      赔付规则聚合（险种线 × 理赔类型）
 * @param hospital        出险医院台账聚合（可为空 = 不在医院网络 = 非定点）
 */
public record ReimbursementAdjustmentRequest(
        BigDecimal eligibleExpense,
        ClaimPayoutRule payoutRule,
        ClaimHospitalNetwork hospital) {

    public ReimbursementAdjustmentRequest {
        if (eligibleExpense == null || eligibleExpense.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("合规费用必须大于0");
        }
        if (payoutRule == null) {
            throw new IllegalArgumentException("赔付规则不能为空");
        }
    }

    /**
     * 报销理算结果值对象（领域服务出参，供 application 组装展示/结算）
     *
     * @param calculation      理算产物（公式参数与应付金额）
     * @param payoutRatioUsed  实际套用的赔付比例（0-100）
     * @param settlementChannel 结算渠道（定点/非定点）
     */
    public record ReimbursementAdjustmentResult(
            ReimbursementCalculation calculation,
            Integer payoutRatioUsed,
            SettlementChannel settlementChannel) {
    }
}
