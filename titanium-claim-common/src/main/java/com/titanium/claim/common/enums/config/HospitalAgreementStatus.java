package com.titanium.claim.common.enums.config;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * 宠物医院协议状态枚举（本域专属，claim-common/enums/config 统一管理）
 * <p>
 * 理赔配置子域医院网络台账（{@code ClaimHospitalNetwork}）的协议生命周期三态，持久化存 code（红线 20）。
 * 出险医院资格校验（宠物险线）仅认定 {@link #ACTIVE} 态医院为定点可赔。
 * </p>
 */
@Getter
public enum HospitalAgreementStatus implements BaseEnum {
    /** 协议有效：定点/直赔资格可用 */
    ACTIVE(1, "ACTIVE", "协议有效"),
    /** 协议暂停：资格冻结，恢复前不按定点比例赔付 */
    SUSPENDED(2, "SUSPENDED", "协议暂停"),
    /** 协议终止：台账留存，不再参与资格校验 */
    TERMINATED(3, "TERMINATED", "协议终止");

    HospitalAgreementStatus(Integer enumCode, String code, String name) {
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
     * @return 协议状态枚举
     */
    public static HospitalAgreementStatus fromCode(String code) {
        return BaseEnum.fromCode(HospitalAgreementStatus.class, code);
    }
}
