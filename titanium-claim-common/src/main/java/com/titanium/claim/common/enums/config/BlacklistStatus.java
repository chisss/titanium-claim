package com.titanium.claim.common.enums.config;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * 黑名单生效状态枚举（本域专属，claim-common/enums/config 统一管理）
 * <p>
 * 理赔配置子域黑名单（{@code ClaimBlacklist}）的生效状态两态，持久化存 code（红线 20）。
 * 仅 {@link #ACTIVE} 态参与反欺诈命中提示。
 * </p>
 */
@Getter
public enum BlacklistStatus implements BaseEnum {
    /** 生效中：参与命中提示 */
    ACTIVE(1, "ACTIVE", "生效中"),
    /** 已撤销：留存记录，不再命中 */
    REVOKED(2, "REVOKED", "已撤销");

    BlacklistStatus(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    /**
     * 按稳定标识 code 反查枚举
     *
     * @param code 稳定标识
     * @return 生效状态枚举
     */
    public static BlacklistStatus fromCode(String code) {
        return BaseEnum.fromCode(BlacklistStatus.class, code);
    }
}
