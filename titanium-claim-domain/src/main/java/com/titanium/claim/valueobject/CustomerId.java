package com.titanium.claim.valueobject;

/**
 * 客户标识值对象
 *
 * @param value 客户唯一标识
 */
public record CustomerId(String value) {

    /**
     * 由字符串构造客户标识
     *
     * @param value 标识字符串
     * @return 客户标识
     */
    public static CustomerId of(String value) {
        return new CustomerId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
