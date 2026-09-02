package com.titanium.claim.valueobject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import com.titanium.claim.common.enums.BenefitSource;
import com.titanium.claim.common.exception.BenefitCalculationException;
import com.titanium.metadata.errorcode.ClaimErrorCode;

/**
 * 身故给付金核算值对象（寿险身故金按受益人份额分配）
 * <p>
 * 身故理赔（{@code ClaimType.DEATH}）的给付金精算模型：以核定给付总额为基数，按受益人约定份额分配到各
 * 受益人。定期寿险/终身寿险的身故金一次性给付，给付后保单责任终止（区别于年金的生存给付逐期推进不终止）。
 * </p>
 * <p>
 * 充血不可变值对象：构造时校验「各受益人比例之和等于 1」与「份额之和等于给付总额」（双重守恒），杜绝分配错漏；
 * 金额来源规则（{@link BenefitSource}）强制携带，杜绝「金额来源不明」的透传金额。
 * 定额给付精算经 {@link #ofBasicSumInsured} 工厂：给付总额 = 基本保额，各受益人按比例分配，
 * 四舍五入尾差调整至最后一位受益人。
 * </p>
 *
 * @param totalBenefit 核定给付总额（身故金基数，由精算来源规则决定，非调用方透传）
 * @param shares       各受益人给付份额明细
 * @param source       给付金额来源规则（精算依据，如基本保额定额给付）
 */
public record BenefitCalculation(
        BigDecimal totalBenefit,
        List<BeneficiaryShare> shares,
        BenefitSource source) {

    public BenefitCalculation {
        if (totalBenefit == null || totalBenefit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BenefitCalculationException(ClaimErrorCode.CLAIM_BENEFIT_AMOUNT_INVALID, "身故给付总额必须大于0");
        }
        if (source == null) {
            throw new BenefitCalculationException(ClaimErrorCode.CLAIM_BENEFIT_AMOUNT_INVALID, "给付金额来源规则不能为空");
        }
        shares = shares == null ? List.of() : List.copyOf(shares);
        if (shares.isEmpty()) {
            throw new BenefitCalculationException(ClaimErrorCode.CLAIM_BENEFIT_SHARE_MISSING, "身故给付受益人份额不能为空");
        }
        BigDecimal ratioSum = shares.stream()
                .map(BeneficiaryShare::benefitRatio)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (ratioSum.compareTo(BigDecimal.ONE) != 0) {
            throw new BenefitCalculationException(ClaimErrorCode.CLAIM_BENEFIT_SHARE_MISMATCH,
                    "受益人比例之和(" + ratioSum + ")必须等于1");
        }
        BigDecimal sum = shares.stream()
                .map(BeneficiaryShare::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(totalBenefit) != 0) {
            throw new BenefitCalculationException(ClaimErrorCode.CLAIM_BENEFIT_SHARE_MISMATCH,
                    "受益人份额之和(" + sum + ")必须等于给付总额(" + totalBenefit + ")");
        }
    }

    /**
     * 定额给付精算工厂：给付总额 = 基本保额，各受益人按约定比例分配（尾差调整至最后一位受益人）。
     *
     * @param basicSumInsured 保单基本保额（policy 域取数）
     * @param specs           受益人份额规格（受益人 ID/姓名/受益比例）
     * @return 精算产物（来源规则 = 基本保额定额给付）
     */
    public static BenefitCalculation ofBasicSumInsured(BigDecimal basicSumInsured, List<BeneficiaryShareSpec> specs) {
        return allocate(basicSumInsured, specs, BenefitSource.BASIC_SUM_INSURED);
    }

    /**
     * 全残给付精算工厂（CLAIM-6）：给付总额 = {@code max(账户价值, 基本保额)}。
     * <p>
     * 全残给付金额按产品条款「基本保额、或账户价值与基本保额孰高」计算。账户价值未取到时
     * （policy 域账户价值查询接口未提供）回落基本保额，保证给付链路的可用性与向后兼容；
     * 比例分配与尾差调整同 {@link #ofBasicSumInsured}。
     * </p>
     *
     * @param accountValue    保单账户价值（可空，空时回落基本保额）
     * @param basicSumInsured 保单基本保额（policy 域取数）
     * @param specs           受益人份额规格（受益人 ID/姓名/受益比例）
     * @return 精算产物（来源规则 = 账户价值与基本保额孰高给付）
     */
    public static BenefitCalculation ofAccountValueMax(BigDecimal accountValue, BigDecimal basicSumInsured,
                                                       List<BeneficiaryShareSpec> specs) {
        BigDecimal total = accountValue == null || accountValue.compareTo(basicSumInsured) < 0
                ? basicSumInsured
                : accountValue;
        return allocate(total, specs, BenefitSource.ACCOUNT_VALUE_MAX);
    }

    /**
     * 通用分配：按受益比例分配给付总额，尾差调整至最后一位受益人（份额守恒由构造器校验）。
     */
    private static BenefitCalculation allocate(BigDecimal total, List<BeneficiaryShareSpec> specs,
                                               BenefitSource source) {
        List<BeneficiaryShareSpec> copy = specs == null ? List.of() : List.copyOf(specs);
        List<BeneficiaryShare> shares = new ArrayList<>(copy.size());
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < copy.size(); i++) {
            BeneficiaryShareSpec spec = copy.get(i);
            BigDecimal amount;
            if (i == copy.size() - 1) {
                // 最后一位受益人吸收四舍五入尾差，保证份额之和精确等于给付总额
                amount = total.subtract(allocated);
            } else {
                amount = total.multiply(spec.benefitRatio()).setScale(2, RoundingMode.HALF_UP);
                allocated = allocated.add(amount);
            }
            shares.add(new BeneficiaryShare(spec.beneficiaryId(), spec.beneficiaryName(), spec.benefitRatio(),
                    amount));
        }
        return new BenefitCalculation(total, shares, source);
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
     * @param benefitRatio 受益比例（0-1 开区间右闭，各受益人比例之和为1）
     * @param amount 应得给付额（= 给付总额 × 受益比例，尾差调整至最后一位）
     */
    public record BeneficiaryShare(
            String beneficiaryId,
            String beneficiaryName,
            BigDecimal benefitRatio,
            BigDecimal amount) {

        public BeneficiaryShare {
            if (benefitRatio == null || benefitRatio.compareTo(BigDecimal.ZERO) <= 0
                    || benefitRatio.compareTo(BigDecimal.ONE) > 0) {
                throw new BenefitCalculationException(ClaimErrorCode.CLAIM_BENEFIT_SHARE_MISMATCH,
                        "受益比例必须大于0且不超过1: " + benefitRatio);
            }
        }
    }

    /**
     * 受益人份额规格（精算入参：不含金额，应得金额由系统按比例计算）
     *
     * @param beneficiaryId 受益人ID
     * @param beneficiaryName 受益人姓名
     * @param benefitRatio 受益比例（0-1 开区间右闭，各受益人比例之和为1）
     */
    public record BeneficiaryShareSpec(
            String beneficiaryId,
            String beneficiaryName,
            BigDecimal benefitRatio) {
    }
}
