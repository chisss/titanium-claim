package com.titanium.claim.common.enums.config;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * 医院结算渠道枚举（宠物险医院网络校验产物：出险医院是否属于直赔网络台账）
 * <p>
 * 定点医院套用台账赔付比例；非定点套用赔付规则非定点档位（{@code NON_DESIGNATED}，
 * 缺省回落基础比例半数）。理赔配置子域专属枚举，按根规约 §3.4.2 置于 common/enums。
 * </p>
 */
@Getter
public enum SettlementChannel implements BaseEnum {

    /** 定点医院（医院网络台账 ACTIVE） */
    DESIGNATED(1, "DESIGNATED", "定点医院"),
    /** 非定点医院（不在台账或协议非 ACTIVE） */
    NON_DESIGNATED(2, "NON_DESIGNATED", "非定点医院");

    SettlementChannel(Integer enumCode, String code, String name) {
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
     * @return 结算渠道枚举；未知 code 返回 {@code null}
     */
    public static SettlementChannel fromCode(String code) {
        return BaseEnum.fromCode(SettlementChannel.class, code);
    }
}
