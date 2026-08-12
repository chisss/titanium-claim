package com.titanium.claim.valueobject;

import java.math.BigDecimal;

import com.titanium.claim.common.exception.InvalidClaimAmountException;

/**
 * 理赔金额值对象
 * <p>
 * 不可变值对象，构造时校验金额非空且大于 0。
 * </p>
 *
 * @param value 理赔金额
 */
public record ClaimAmount(BigDecimal value) {

    public ClaimAmount {
        validate(value);
    }

    /**
     * 由 {@link BigDecimal} 构造理赔金额
     *
     * @param value 金额
     * @return 理赔金额值对象
     */
    public static ClaimAmount of(BigDecimal value) {
        return new ClaimAmount(value);
    }

    /**
     * 由字符串构造理赔金额
     *
     * @param value 金额字符串
     * @return 理赔金额值对象
     */
    public static ClaimAmount of(String value) {
        return of(new BigDecimal(value));
    }

    /**
     * 由 double 构造理赔金额
     *
     * @param value 金额
     * @return 理赔金额值对象
     */
    public static ClaimAmount of(double value) {
        return of(new BigDecimal(value));
    }

    /**
     * 金额校验：非空且大于 0
     *
     * @param value 金额
     */
    private static void validate(BigDecimal value) {
        if (value == null) {
            throw new InvalidClaimAmountException();
        }
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidClaimAmountException();
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
