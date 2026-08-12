package com.titanium.claim.valueobject;

/**
 * 保单标识值对象
 *
 * @param value 保单唯一标识
 */
public record PolicyId(String value) {

    /**
     * 由字符串构造保单标识
     *
     * @param value 标识字符串
     * @return 保单标识
     */
    public static PolicyId of(String value) {
        return new PolicyId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
