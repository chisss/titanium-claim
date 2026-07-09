package com.titanium.claim.common.enums;

import com.titanium.claim.common.constant.ClaimConstants;
import com.titanium.claim.common.exception.InvalidClaimStatusException;
import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * 理赔状态枚举（本域专属，置于 claim-common/enums 统一管理）
 */
@Getter
public enum ClaimStatus implements BaseEnum {
    PENDING(1, ClaimConstants.CLAIM_STATUS_PENDING, "待处理"),
    PROCESSING(2, ClaimConstants.CLAIM_STATUS_PROCESSING, "处理中"),
    APPROVED(3, ClaimConstants.CLAIM_STATUS_APPROVED, "已批准"),
    REJECTED(4, ClaimConstants.CLAIM_STATUS_REJECTED, "已拒绝"),
    PAID(5, ClaimConstants.CLAIM_STATUS_PAID, "已支付");

    /**
     * 持久化数字码
     */
    private final Integer enumCode;

    /**
     * 业务编码
     */
    private final String code;

    /**
     * 中文名称
     */
    private final String name;

    ClaimStatus(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }

    /**
     * 按 code 反查理赔状态（委托 {@link BaseEnum}），未匹配抛出非法状态异常。
     *
     * @param code 理赔状态码
     * @return 匹配的枚举
     */
    public static ClaimStatus fromCode(String code) {
        ClaimStatus status = BaseEnum.fromCode(ClaimStatus.class, code);
        if (status == null) {
            throw new InvalidClaimStatusException();
        }
        return status;
    }

    /**
     * 理赔状态描述（兼容旧调用）。
     *
     * @return 与 {@link #getName()} 等价的中文名称
     * @deprecated 请改用 {@link #getName()}，本方法仅为兼容既有调用点保留
     */
    @Deprecated
    public String getDescription() {
        return name;
    }

    /**
     * 按描述反查理赔状态（兼容旧调用），未匹配抛出非法状态异常。
     *
     * @param description 理赔状态描述
     * @return 匹配的枚举
     * @deprecated 请改用 {@link #fromCode(String)}，本方法仅为兼容既有调用点保留
     */
    @Deprecated
    public static ClaimStatus fromDescription(String description) {
        ClaimStatus status = BaseEnum.fromName(ClaimStatus.class, description);
        if (status == null) {
            throw new InvalidClaimStatusException();
        }
        return status;
    }

    @Override
    public String toString() {
        return code;
    }
}
