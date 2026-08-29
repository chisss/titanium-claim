package com.titanium.claim.valueobject;

import com.titanium.common.util.SnowflakeIdGenerator;

/**
 * 理赔案件标识值对象（Claim 聚合根标识）
 * <p>
 * 作为 {@code @TargetAggregateIdentifier} 的命令路由键，{@link #toString()} 必须返回裸 id 字符串，
 * 否则 Axon 聚合路由失效。
 * </p>
 *
 * @param value 理赔案件唯一标识
 */
public record ClaimId(String value) {

    /**
     * 生成新的理赔案件标识（UUID）
     *
     * @return 理赔案件标识
     */
    public static ClaimId generate() {
        return new ClaimId(SnowflakeIdGenerator.generate());
    }

    /**
     * 由字符串构造理赔案件标识
     *
     * @param value 标识字符串
     * @return 理赔案件标识
     */
    public static ClaimId of(String value) {
        return new ClaimId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
