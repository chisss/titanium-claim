package com.titanium.claim.valueobject;

import java.math.BigDecimal;
import java.util.List;

/**
 * 身故给付金核算值对象（寿险身故金按受益人份额分配）
 * <p>
 * 身故理赔（{@code ClaimType.DEATH}）的给付金核算模型：以核定给付总额为基数，按受益人约定份额分配到各
 * 受益人。定期寿险/终身寿险的身故金一次性给付，给付后保单责任终止（区别于年金的生存给付逐期推进不终止）。
 * </p>
 * <p>
 * 充血不可变值对象：构造时校验各受益人份额之和等于给付总额（分配守恒），杜绝分配错漏；
 * {@link #payoutOf(String)} 按受益人查其应得给付额。
 * </p>
 *
 * @param totalBenefit  核定给付总额（身故金基数，如 max(账户价值, 基本保额) 由条款约定，此处为核算结果）
 * @param shares        各受益人给付份额明细
 */
public record BenefitCalculation(
        BigDecimal totalBenefit,
        List<BeneficiaryShare> shares) {

    public BenefitCalculation {
        if (totalBenefit == null || totalBenefit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("身故给付总额必须大于0");
        }
        shares = shares == null ? List.of() : List.copyOf(shares);
        if (shares.isEmpty()) {
            throw new IllegalArgumentException("身故给付受益人份额不能为空");
        }
        BigDecimal sum = shares.stream()
                .map(BeneficiaryShare::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(totalBenefit) != 0) {
            throw new IllegalArgumentException(
                    "受益人份额之和(" + sum + ")必须等于给付总额(" + totalBenefit + ")");
        }
    }

    /**
     * 查指定受益人的应得给付额。
     *
     * @param beneficiaryId 受益人ID
     * @return 应得给付额；无该受益人返回 {@code BigDecimal.ZERO}
     */
    public BigDecimal payoutOf(String beneficiaryId) {
        return shares.stream()
                .filter(s -> s.beneficiaryId().equals(beneficiaryId))
                .map(BeneficiaryShare::amount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    /**
     * 受益人给付份额明细
     *
     * @param beneficiaryId 受益人ID
     * @param beneficiaryName 受益人姓名
     * @param benefitRatio 受益比例（0-1，各受益人比例之和为1）
     * @param amount 应得给付额（= 给付总额 × 受益比例）
     */
    public record BeneficiaryShare(
            String beneficiaryId,
            String beneficiaryName,
            BigDecimal benefitRatio,
            BigDecimal amount) {
    }
}
